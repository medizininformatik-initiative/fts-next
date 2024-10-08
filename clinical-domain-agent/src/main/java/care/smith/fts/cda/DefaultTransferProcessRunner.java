package care.smith.fts.cda;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
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
    TransferProcessInstance transferProcessInstance = new TransferProcessInstance(process);
    transferProcessInstance.execute(pids);
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

  public class TransferProcessInstance {

    private final TransferProcessDefinition process;

    private final AtomicLong skippedBundles = new AtomicLong();
    private final AtomicLong sentBundles = new AtomicLong();
    private final AtomicReference<Phase> phase = new AtomicReference<>(Phase.QUEUED);

    public TransferProcessInstance(TransferProcessDefinition process) {
      this.process = process;
    }

    public void execute(List<String> pids) {
      phase.set(Phase.RUNNING);
      process
          .cohortSelector()
          .selectCohort(pids)
          .flatMap(process.dataSelector()::select)
          .flatMap(process.deidentificator()::deidentify)
          .limitRate(sendConcurrency * 2)
          .onBackpressureBuffer(sendConcurrency * 2)
          .flatMap(b -> process.bundleSender().send(b), sendConcurrency)
          .subscribe(
              r -> sentBundles.incrementAndGet(),
              this::handleBundleError,
              () -> phase.compareAndSet(Phase.RUNNING, Phase.COMPLETED));
    }

    private void handleBundleError(Throwable e) {
      skippedBundles.incrementAndGet();
      log.error("Skipping bundle: {}", e.getMessage());
      phase.set(Phase.ERROR);
    }

    public Status status(String processId) {
      return new Status(processId, phase.get(), sentBundles.get(), skippedBundles.get());
    }
  }
}
