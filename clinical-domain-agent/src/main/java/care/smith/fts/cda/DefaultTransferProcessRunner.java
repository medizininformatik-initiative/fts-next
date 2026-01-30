package care.smith.fts.cda;

import static care.smith.fts.util.JsonLogFormatter.asJson;
import static care.smith.fts.util.NanoIdUtils.nanoId;
import static java.util.stream.Stream.concat;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.cda.BundleSender.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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
    var removeBefore = LocalDateTime.now().minus(config.processTtl);
    var forRemoval =
        instances.values().stream()
            .filter(inst -> inst.status().mayBeRemoved(removeBefore))
            .toList();
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
    var transferProcessInstance = instances.get(processId);
    if (transferProcessInstance != null) {
      return Mono.just(transferProcessInstance.status());
    } else {
      return Mono.justOrEmpty(
              queued.stream().filter(q -> q.processId().equals(processId)).findFirst())
          .map(TransferProcessInstance::status)
          .switchIfEmpty(
              Mono.error(
                  new IllegalStateException("No transfer process with processId: " + processId)));
    }
  }

  private synchronized void onComplete() {
    var next = queued.poll();
    if (next != null) {
      next.execute();
      instances.put(next.processId(), next);
    }
  }

  public class TransferProcessInstance {

    private final TransferProcessDefinition process;
    private final AtomicReference<TransferProcessStatus> status;
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
          .subscribe();
      log.info("[Process {}] Started", processId());
    }

    private Flux<ConsentedPatient> selectCohort(List<String> identifiers) {
      return process
          .cohortSelector()
          .selectCohort(identifiers)
          .doOnNext(b -> status.updateAndGet(TransferProcessStatus::incTotalPatients))
          .doOnError(e -> status.updateAndGet(s -> s.setPhase(Phase.FATAL)))
          .onErrorComplete();
    }

    private Flux<ConsentedPatientBundle> selectData(Flux<ConsentedPatient> cohortSelection) {
      return cohortSelection
          .flatMap(this::selectDataForPatient)
          .doOnNext(b -> status.updateAndGet(TransferProcessStatus::incTotalBundles));
    }

    private Flux<ConsentedPatientBundle> selectDataForPatient(ConsentedPatient patient) {
      return process
          .dataSelector()
          .select(patient)
          .doOnError(e -> logError("select data", patient.identifier(), e));
    }

    private void logError(String step, String patientIdentifier, Throwable e) {
      var msg = "[Process {}] Failed to {} for patient {}. {}";
      log.error(
          msg, processId(), step, patientIdentifier, log.isDebugEnabled() ? e : e.getMessage());
    }

    public record PatientContext<T>(T data, ConsentedPatient consentedPatient) {}

    private Flux<PatientContext<TransportBundle>> deidentify(
        Flux<ConsentedPatientBundle> dataSelection) {
      return dataSelection
          .flatMap(this::deidentifyForPatient)
          .doOnNext(b -> status.updateAndGet(TransferProcessStatus::incDeidentifiedBundles));
    }

    private Mono<PatientContext<TransportBundle>> deidentifyForPatient(
        ConsentedPatientBundle bundle) {
      return process
          .deidentificator()
          .deidentify(bundle)
          .doOnError(e -> logError("deidentify bundle", bundle.consentedPatient().identifier(), e))
          .map(t -> new PatientContext<>(t, bundle.consentedPatient()));
    }

    private Flux<Result> sendBundles(Flux<PatientContext<TransportBundle>> deidentification) {
      return deidentification
          .flatMap(this::sendBundleForPatient, config.maxSendConcurrency)
          .doOnNext(b -> status.updateAndGet(TransferProcessStatus::incSentBundles))
          .onErrorContinue((e, r) -> status.updateAndGet(TransferProcessStatus::incSkippedBundles));
    }

    private Mono<Result> sendBundleForPatient(PatientContext<TransportBundle> b) {
      return process
          .bundleSender()
          .send(b.data())
          .doOnError(e -> logError("send bundle", b.consentedPatient().identifier(), e));
    }

    private void onComplete() {
      var status = this.status.updateAndGet(s -> s.phase() != Phase.FATAL ? checkCompletion(s) : s);
      log.info("[Process {}] Finished with: {}", processId(), status.phase());
    }

    private TransferProcessStatus checkCompletion(TransferProcessStatus s) {
      return s.skippedBundles() == 0
          ? s.setPhase(Phase.COMPLETED)
          : s.setPhase(Phase.COMPLETED_WITH_ERROR);
    }

    public TransferProcessStatus status() {
      return status.get();
    }

    private String processId() {
      return status.get().processId();
    }

    public Boolean isRunning() {
      return status().phase() == Phase.RUNNING;
    }
  }
}
