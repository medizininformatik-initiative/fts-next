package care.smith.fts.cda;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.cda.BundleSender.Result;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class DefaultTransferProcessRunner implements TransferProcessRunner {

  private final Map<String, TransferProcessInstance> instances = new ConcurrentHashMap<>();

  @Value("${transfer.sendConcurrency:32}")
  private int sendConcurrency = 32;

  @Override
  public String start(TransferProcessDefinition process, List<String> pids) {
    var processId = UUID.randomUUID().toString();
    log.info("Run process with processId: {}", processId);
    log.debug("Using a sendConcurrency of {}", sendConcurrency);
    TransferProcessInstance transferProcessInstance =
        new TransferProcessInstance(process, processId);
    transferProcessInstance.execute(pids);
    instances.put(processId, transferProcessInstance);
    return processId;
  }

  @Override
  public Mono<TransferProcessStatus> status(String processId) {
    TransferProcessInstance transferProcessInstance = instances.get(processId);
    if (transferProcessInstance != null) {
      return Mono.just(transferProcessInstance.status());
    } else {
      return Mono.error(new IllegalArgumentException());
    }
  }

  public class TransferProcessInstance {

    private final TransferProcessDefinition process;

    private final AtomicReference<TransferProcessStatus> status;

    public TransferProcessInstance(TransferProcessDefinition process, String processId) {
      this.process = process;
      status = new AtomicReference<>(TransferProcessStatus.create(processId));
    }

    public void execute(List<String> pids) {
      status.updateAndGet(s -> s.withPhase(Phase.RUNNING));
      selectCohort(pids)
          .transform(this::selectData)
          .transform(this::deidentify)
          .transform(this::sendBundles)
          .doOnComplete(this::updateStatus)
          .subscribe();
    }

    private Flux<ConsentedPatient> selectCohort(List<String> pids) {
      return process
          .cohortSelector()
          .selectCohort(pids)
          .doOnNext(b -> status.updateAndGet(TransferProcessStatus::incTotalPatients))
          .doOnError(e -> status.updateAndGet(s -> s.withPhase(Phase.FATAL)))
          .onErrorComplete();
    }

    private Flux<ConsentedPatientBundle> selectData(Flux<ConsentedPatient> cohortSelection) {
      return cohortSelection
          .flatMap(process.dataSelector()::select)
          .doOnNext(b -> status.updateAndGet(TransferProcessStatus::incTotalBundles));
    }

    private Flux<TransportBundle> deidentify(Flux<ConsentedPatientBundle> dataSelection) {
      return dataSelection
          .flatMap(process.deidentificator()::deidentify)
          .doOnNext(b -> status.updateAndGet(TransferProcessStatus::incDeidentifiedBundles));
    }

    private Flux<Result> sendBundles(Flux<TransportBundle> deidentification) {
      return deidentification
          .flatMap(b -> process.bundleSender().send(b), sendConcurrency)
          .doOnNext(b -> status.updateAndGet(TransferProcessStatus::incSentBundles))
          .onErrorContinue((e, r) -> status.updateAndGet(TransferProcessStatus::incSkippedBundles));
    }

    private void updateStatus() {
      status.updateAndGet(s -> s.phase() != Phase.FATAL ? checkCompletion(s) : s);
    }

    private TransferProcessStatus checkCompletion(TransferProcessStatus s) {
      return s.skippedBundles() == 0
          ? s.withPhase(Phase.COMPLETED)
          : s.withPhase(Phase.COMPLETED_WITH_ERROR);
    }

    public TransferProcessStatus status() {
      return status.get();
    }
  }
}
