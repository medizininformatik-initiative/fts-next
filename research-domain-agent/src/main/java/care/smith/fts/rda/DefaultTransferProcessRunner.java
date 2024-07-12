package care.smith.fts.rda;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.api.rda.Deidentificator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class DefaultTransferProcessRunner implements TransferProcessRunner {

  private final Map<String, TransferProcessInstance> instances = new HashMap<>();

  @Override
  public String start(TransferProcessDefinition process, Mono<TransportBundle> data) {
    var processId = UUID.randomUUID().toString();
    log.info("Run process with processId: {}", processId);
    TransferProcessInstance transferProcessInstance = new TransferProcessInstance(process);
    transferProcessInstance.execute(data);
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
    private final Deidentificator deidentificator;
    private final BundleSender bundleSender;
    private final AtomicReference<Phase> phase;
    private final AtomicLong receivedResources;
    private final AtomicLong sentResources;

    public TransferProcessInstance(TransferProcessDefinition process) {
      deidentificator = process.deidentificator();
      bundleSender = process.bundleSender();

      phase = new AtomicReference<>(Phase.QUEUED);
      receivedResources = new AtomicLong();
      sentResources = new AtomicLong();
    }

    public void execute(Mono<TransportBundle> data) {
      phase.set(Phase.RUNNING);
      data.doOnNext(
              b ->
                  log.debug(
                      "processing patient bundle, resources: {}", b.bundle().getEntry().size()))
          .doOnNext(b -> receivedResources.getAndAdd(b.bundle().getEntry().size()))
          .flatMap(deidentificator::replaceIds)
          .doOnNext(b -> sentResources.getAndAdd(b.getEntry().size()))
          .flatMap(bundleSender::send)
          .doOnError(err -> log.info("Could not process patient: {}", err.getMessage()))
          .doOnError(err -> phase.set(Phase.ERROR))
          .map(r -> new Result(receivedResources.get(), sentResources.get()))
          .doOnNext(b -> phase.set(Phase.COMPLETED))
          .onErrorComplete()
          .subscribe();
    }

    public Status status(String processId) {
      return new Status(processId, phase.get(), receivedResources.get(), sentResources.get());
    }
  }
}
