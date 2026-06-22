package care.smith.fts.rda;

import static care.smith.fts.util.JsonLogFormatter.asJson;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.api.rda.Deidentificator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import java.time.Instant;
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
  private final ObjectMapper om;
  private final BulkheadRegistry bulkheadRegistry;
  private final TransferProcessRunnerConfig config;

  public DefaultTransferProcessRunner(
      ObjectMapper om, BulkheadRegistry bulkheadRegistry, TransferProcessRunnerConfig config) {
    this.om = om;
    this.bulkheadRegistry = bulkheadRegistry;
    this.config = config;
  }

  @Override
  public StartResult start(TransferProcessDefinition process, Mono<TransportBundle> data) {
    removeOldProcesses();
    var destination = process.bundleSender().destinationId();
    var bulkhead = bulkheadRegistry.bulkhead(destination);
    if (!bulkhead.tryAcquirePermission()) {
      log.info("Destination {} saturated, rejecting admission", destination);
      return new StartResult.Rejected();
    }
    var processId = UUID.randomUUID().toString();
    log.info("Run process with processId: {}", processId);
    log.info("Project configuration: {}", asJson(om, process.rawConfig()));
    TransferProcessInstance transferProcessInstance = new TransferProcessInstance(process);
    transferProcessInstance.execute(data, bulkhead);
    instances.put(processId, transferProcessInstance);
    return new StartResult.Accepted(processId);
  }

  private void removeOldProcesses() {
    var cutoff = Instant.now().minus(config.processTtl());
    instances.entrySet().removeIf(e -> e.getValue().isFinishedBefore(cutoff));
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
    private final Deidentificator deidentificator;
    private final BundleSender bundleSender;
    private final AtomicReference<Phase> phase;
    private final AtomicLong receivedResources;
    private final AtomicLong sentResources;
    private final AtomicReference<Instant> finishedAt;

    public TransferProcessInstance(TransferProcessDefinition process) {
      deidentificator = process.deidentificator();
      bundleSender = process.bundleSender();

      phase = new AtomicReference<>(Phase.RUNNING);
      receivedResources = new AtomicLong();
      sentResources = new AtomicLong();
      finishedAt = new AtomicReference<>();
    }

    public void execute(Mono<TransportBundle> data, Bulkhead bulkhead) {
      data.doOnNext(
              b ->
                  log.debug(
                      "processing patient bundle, resources: {}", b.bundle().getEntry().size()))
          .doOnNext(b -> receivedResources.getAndAdd(b.bundle().getEntry().size()))
          .flatMap(deidentificator::deidentify)
          .doOnNext(b -> sentResources.getAndAdd(b.getEntry().size()))
          .flatMap(bundleSender::send)
          .doOnError(err -> log.info("Could not process patient: {}", err.getMessage()))
          .doOnError(err -> log.trace("The exception:", err))
          .doOnError(err -> phase.set(Phase.ERROR))
          .map(r -> new Result(receivedResources.get(), sentResources.get()))
          .doOnNext(b -> phase.set(Phase.COMPLETED))
          .doFinally(
              s -> {
                finishedAt.compareAndSet(null, Instant.now());
                bulkhead.onComplete();
              })
          .onErrorComplete()
          .doOnSuccess(ignored -> phase.compareAndSet(Phase.RUNNING, Phase.COMPLETED))
          .subscribe();
    }

    public boolean isFinishedBefore(Instant cutoff) {
      var finished = finishedAt.get();
      return finished != null && finished.isBefore(cutoff);
    }

    public Status status(String processId) {
      return new Status(processId, phase.get(), receivedResources.get(), sentResources.get());
    }
  }
}
