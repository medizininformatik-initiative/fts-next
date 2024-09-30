package care.smith.fts.cda;

import care.smith.fts.api.*;
import care.smith.fts.api.cda.BundleSender;
import care.smith.fts.api.cda.BundleSender.Result;
import care.smith.fts.api.cda.CohortSelector;
import care.smith.fts.api.cda.DataSelector;
import care.smith.fts.api.cda.Deidentificator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class DefaultTransferProcessRunner implements TransferProcessRunner {

  private final Map<String, TransferProcessInstance> instances = new ConcurrentHashMap<>();

  @Override
  public String start(TransferProcessDefinition process, List<String> pids) {
    var processId = UUID.randomUUID().toString();
    log.info("Run process with processId: {}", processId);
    TransferProcessInstance transferProcessInstance = new TransferProcessInstance(process, pids);
    transferProcessInstance.execute();
    instances.put(processId, transferProcessInstance);
    return processId;
  }

  @Override
  public Mono<Status> status(String processId) {
    TransferProcessInstance transferProcessInstance = instances.get(processId);
    if (transferProcessInstance != null) {
      return Mono.just(transferProcessInstance.status(processId));
    } else {
      return Mono.error(new IllegalArgumentException());
    }
  }

  public static class TransferProcessInstance {

    private final CohortSelector cohortSelector;
    private final DataSelector dataSelector;
    private final Deidentificator deidentificator;
    private final AtomicLong skippedPatients;
    private final BundleSender bundleSender;
    private final List<String> pids;
    private final AtomicLong sentBundles;
    private final AtomicReference<Phase> phase;

    public TransferProcessInstance(TransferProcessDefinition process, List<String> pids) {
      cohortSelector = process.cohortSelector();
      dataSelector = process.dataSelector();
      deidentificator = process.deidentificator();
      bundleSender = process.bundleSender();
      this.pids = pids;

      skippedPatients = new AtomicLong();
      sentBundles = new AtomicLong();
      phase = new AtomicReference<>(Phase.QUEUED);
    }

    public void execute() {
      phase.set(Phase.RUNNING);
      cohortSelector
          .selectCohort(pids)
          .doOnError(e -> phase.set(Phase.ERROR))
          .flatMap(this::executePatient)
          .doOnComplete(() -> phase.set(Phase.COMPLETED))
          .onErrorComplete()
          .subscribe();
    }

    private Mono<Result> executePatient(ConsentedPatient patient) {
      return dataSelector
          .select(patient)
          .map(b -> new ConsentedPatientBundle(b, patient))
          .flatMap(deidentificator::deidentify)
          .as(bundleSender::send)
          .doOnNext(r -> sentBundles.getAndAdd(r.bundleCount()))
          .doOnError(e -> skippedPatients.incrementAndGet())
          .doOnError(e -> log.error("Skipping patient: {}", e.getMessage()))
          .onErrorResume(e -> Mono.just(new Result(0)));
    }

    public Status status(String processId) {
      return new Status(processId, phase.get(), sentBundles.get(), skippedPatients.get());
    }
  }
}
