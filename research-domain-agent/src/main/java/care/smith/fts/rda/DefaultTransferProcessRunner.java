package care.smith.fts.rda;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.api.rda.Deidentificator;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class DefaultTransferProcessRunner implements TransferProcessRunner {

  @Override
  public Mono<Result> run(TransferProcessDefinition process, Mono<TransportBundle> data) {
    return runProcess(data, process.bundleSender(), process.deidentificator());
  }

  private static Mono<Result> runProcess(
      Mono<TransportBundle> data, BundleSender bundleSender, Deidentificator deidentificator) {
    var receivedResources = new AtomicLong();
    var sentResources = new AtomicLong();
    return data.doOnNext(
            b ->
                log.debug("processing patient bundle, resources: {}", b.bundle().getEntry().size()))
        .doOnNext(b -> receivedResources.getAndAdd(b.bundle().getEntry().size()))
        .flatMap(deidentificator::replaceIds)
        .doOnNext(b -> sentResources.getAndAdd(b.getEntry().size()))
        .flatMap(bundleSender::send)
        .doOnError(err -> log.info("Could not process patient: {}", err.getMessage()))
        .map(r -> new Result(receivedResources.get(), sentResources.get()));
  }
}
