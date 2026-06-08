package care.smith.fts.cda;

import static care.smith.fts.util.AgentConfiguration.MAX_OUTBOUND_FANOUT;
import static care.smith.fts.util.JsonLogFormatter.asJson;
import static care.smith.fts.util.NanoIdUtils.nanoId;
import static java.util.stream.Stream.concat;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.cda.BundleSender.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class DefaultTransferProcessRunner implements TransferProcessRunner {

  private final Map<String, TransferProcessInstance> instances = new HashMap<>();
  private final Queue<TransferProcessInstance> queued = new LinkedList<>() {};
  private final TransferProcessRunnerConfig config;
  private final ObjectMapper om;

  public DefaultTransferProcessRunner(
      @Autowired ObjectMapper om, @Autowired TransferProcessRunnerConfig config) {
    this.om = om;
    this.config = config;
  }

  @Override
  public String start(TransferProcessDefinition process, List<String> identifiers) {
    var processId = nanoId(6);
    log.info("[Process {}] Created, config: {}", processId, asJson(om, process.rawConfig()));
    var transferProcessInstance = new TransferProcessInstance(process, processId, identifiers);

    startOrQueue(processId, transferProcessInstance);

    return processId;
  }

  private synchronized void startOrQueue(
      String processId, TransferProcessInstance transferProcessInstance) {
    removeOldProcesses();
    if (runningInstances() < config.maxConcurrentProcesses) {
      transferProcessInstance.execute();
      instances.put(processId, transferProcessInstance);
    } else {
      log.info("[Process {}] Queued", processId);
      queued.add(transferProcessInstance);
    }
  }

  private synchronized long runningInstances() {
    return instances.values().stream().filter(TransferProcessInstance::isRunning).count();
  }

  private synchronized void removeOldProcesses() {
    var removeBefore = Instant.now().minus(config.processTtl);
    var forRemoval =
        instances.values().stream()
            .filter(inst -> inst.status().mayBeRemoved(removeBefore))
            .toList();
    if (!forRemoval.isEmpty()) {
      log.trace(
          "[Process Runner] Removing {} old processes older than {}",
          forRemoval.size(),
          removeBefore);
    }
    forRemoval.forEach(p -> instances.remove(p.processId()));
  }

  @Override
  public synchronized Mono<List<TransferProcessStatus>> statuses() {
    removeOldProcesses();
    var statuses =
        concat(
                instances.values().stream().map(TransferProcessInstance::status),
                queued.stream().map(TransferProcessInstance::status))
            .toList();
    return Mono.just(statuses);
  }

  @Override
  public synchronized Mono<TransferProcessStatus> status(String processId) {
    return findInstance(processId).map(TransferProcessInstance::status);
  }

  @Override
  public synchronized Mono<List<PatientError>> failedPatients(String processId) {
    return findInstance(processId).map(TransferProcessInstance::failedPatients);
  }

  private Mono<TransferProcessInstance> findInstance(String processId) {
    var instance = instances.get(processId);
    if (instance != null) {
      return Mono.just(instance);
    }
    return Mono.justOrEmpty(
            queued.stream().filter(q -> q.processId().equals(processId)).findFirst())
        .switchIfEmpty(
            Mono.error(
                new IllegalStateException("No transfer process with processId: " + processId)));
  }

  private synchronized void onComplete() {
    var next = queued.poll();
    if (next != null) {
      log.trace("[Process Runner] Dequeued process {} from queue", next.processId());
      next.execute();
      instances.put(next.processId(), next);
    } else {
      log.trace("[Process Runner] Queue empty, no process to dequeue");
    }
  }

  public class TransferProcessInstance {

    private final TransferProcessDefinition process;
    private final AtomicReference<TransferProcessStatus> status;
    private final Queue<PatientError> failedPatients = new ConcurrentLinkedQueue<>();
    private final List<String> identifiers;

    public TransferProcessInstance(
        TransferProcessDefinition process, String processId, List<String> identifiers) {
      this.process = process;
      status = new AtomicReference<>(TransferProcessStatus.create(processId));
      this.identifiers = identifiers;
    }

    public void execute() {
      status.updateAndGet(s -> s.setPhase(Phase.RUNNING));
      selectCohort(identifiers)
          .transform(this::selectData)
          .transform(this::deidentify)
          .transform(this::sendBundles)
          .doOnComplete(this::onComplete)
          .doOnComplete(DefaultTransferProcessRunner.this::onComplete)
          .doOnError(
              e -> {
                status.updateAndGet(s -> s.setPhase(Phase.FATAL));
                log.error("[Process {}] Unexpected pipeline error", processId(), e);
                DefaultTransferProcessRunner.this.onComplete();
              })
          .subscribe();
      log.info("[Process {}] Started", processId());
    }

    private Flux<ConsentedPatient> selectCohort(List<String> identifiers) {
      log.trace("[Process {}] selectCohort with {} identifiers", processId(), identifiers.size());
      return process
          .cohortSelector()
          .selectCohort(identifiers)
          .doOnNext(
              p -> {
                status.updateAndGet(TransferProcessStatus::incTotalPatients);
                log.trace(
                    "[Process {}] selectCohort emitted patient {}", processId(), p.identifier());
              })
          .doOnError(
              e -> {
                status.updateAndGet(s -> s.setPhase(Phase.FATAL));
                log.error("[Process {}] Cohort selection failed", processId(), e);
              })
          .onErrorComplete();
    }

    private Flux<ConsentedPatientBundle> selectData(Flux<ConsentedPatient> cohortSelection) {
      return cohortSelection
          .doOnNext(
              p -> log.trace("[Process {}] selectData for patient {}", processId(), p.identifier()))
          // Bound the per-patient fan-out to the shared outbound connection budget so this stage
          // never dispatches more concurrent upstream requests than the pool can hold.
          .flatMap(this::selectDataForPatient, MAX_OUTBOUND_FANOUT)
          .doOnNext(
              b -> {
                status.updateAndGet(TransferProcessStatus::incTotalBundles);
                var msg = "[Process {}] selectData produced bundle for patient {}";
                log.trace(msg, processId(), b.consentedPatient().identifier());
              })
          .doOnComplete(() -> log.trace("[Process {}] selectData completed", processId()));
    }

    private Flux<ConsentedPatientBundle> selectDataForPatient(ConsentedPatient patient) {
      log.trace("[Process {}] selectDataForPatient {}", processId(), patient.identifier());
      return process
          .dataSelector()
          .select(patient)
          .doOnNext(
              b -> {
                var msg = "[Process {}] selectDataForPatient {} produced {} entries";
                log.trace(msg, processId(), patient.identifier(), b.bundle().getEntry().size());
              })
          .doOnComplete(
              () -> {
                var msg = "[Process {}] selectDataForPatient {} completed";
                log.trace(msg, processId(), patient.identifier());
              })
          .onErrorResume(e -> handlePatientError(patient.identifier(), Step.SELECT_DATA, e));
    }

    private <T> Mono<T> handlePatientError(String patientId, Step step, Throwable e) {
      logError(step, patientId, e);
      // Increment counter first so a concurrent reader never observes more queued
      // failures than skippedBundles reports.
      status.updateAndGet(TransferProcessStatus::incSkippedBundles);
      failedPatients.add(new PatientError(patientId, step, e.getMessage()));
      return Mono.empty();
    }

    private void logError(Step step, String patientIdentifier, Throwable e) {
      var msg = "[Process {}] Failed to {} for patient {}. {}";
      log.error(
          msg, processId(), step, patientIdentifier, log.isDebugEnabled() ? e : e.getMessage());
    }

    public record PatientContext<T>(T data, ConsentedPatient consentedPatient) {}

    private Flux<PatientContext<TransportBundle>> deidentify(
        Flux<ConsentedPatientBundle> dataSelection) {
      var beforeMsg = "[Process {}] deidentify for patient {}";
      return dataSelection
          .doOnNext(b -> log.trace(beforeMsg, processId(), b.consentedPatient().identifier()))
          // Bound the per-patient fan-out to the shared outbound connection budget so this stage
          // never dispatches more concurrent upstream requests than the pool can hold.
          .flatMap(this::deidentifyForPatient, MAX_OUTBOUND_FANOUT)
          .doOnNext(
              b -> {
                status.updateAndGet(TransferProcessStatus::incDeidentifiedBundles);
                var doneMsg = "[Process {}] deidentify completed for patient {}";
                log.trace(doneMsg, processId(), b.consentedPatient().identifier());
              });
    }

    private Mono<PatientContext<TransportBundle>> deidentifyForPatient(
        ConsentedPatientBundle bundle) {
      var patientId = bundle.consentedPatient().identifier();
      log.trace("[Process {}] deidentifyForPatient {}", processId(), patientId);
      var producedMsg = "[Process {}] deidentifyForPatient {} produced transport bundle";
      var completedMsg = "[Process {}] deidentifyForPatient {} completed";
      return process
          .deidentificator()
          .deidentify(bundle)
          .doOnNext(t -> log.trace(producedMsg, processId(), patientId))
          .doOnSuccess(v -> log.trace(completedMsg, processId(), patientId))
          .map(t -> new PatientContext<>(t, bundle.consentedPatient()))
          .onErrorResume(e -> handlePatientError(patientId, Step.DEIDENTIFY, e));
    }

    private Flux<Result> sendBundles(Flux<PatientContext<TransportBundle>> deidentification) {
      var beforeMsg = "[Process {}] sendBundles for patient {}";
      return deidentification
          .doOnNext(b -> log.trace(beforeMsg, processId(), b.consentedPatient().identifier()))
          .flatMap(this::sendBundleForPatient, config.maxSendConcurrency)
          .doOnNext(b -> status.updateAndGet(TransferProcessStatus::incSentBundles));
    }

    private Mono<Result> sendBundleForPatient(PatientContext<TransportBundle> b) {
      var patientId = b.consentedPatient().identifier();
      log.trace("[Process {}] sendBundleForPatient {}", processId(), patientId);
      var successMsg = "[Process {}] sendBundleForPatient {} succeeded";
      return process
          .bundleSender()
          .send(b.data())
          .doOnSuccess(r -> log.trace(successMsg, processId(), patientId))
          .onErrorResume(e -> handlePatientError(patientId, Step.SEND_BUNDLE, e));
    }

    private void onComplete() {
      var status = this.status.updateAndGet(s -> s.phase() != Phase.FATAL ? checkCompletion(s) : s);
      log.info("[Process {}] Finished with: {}", processId(), status.phase());
      log.trace(
          "[Process {}] Summary: totalPatients={}, totalBundles={}, deidentifiedBundles={}, "
              + "sentBundles={}, skippedBundles={}",
          processId(),
          status.totalPatients(),
          status.totalBundles(),
          status.deidentifiedBundles(),
          status.sentBundles(),
          status.skippedBundles());
    }

    private TransferProcessStatus checkCompletion(TransferProcessStatus s) {
      return s.skippedBundles() == 0
          ? s.setPhase(Phase.COMPLETED)
          : s.setPhase(Phase.COMPLETED_WITH_ERROR);
    }

    public TransferProcessStatus status() {
      return status.get();
    }

    public List<PatientError> failedPatients() {
      return List.copyOf(failedPatients);
    }

    private String processId() {
      return status.get().processId();
    }

    public Boolean isRunning() {
      return status().phase() == Phase.RUNNING;
    }
  }
}
