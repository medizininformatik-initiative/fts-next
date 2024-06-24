package care.smith.fts.rda;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.rda.BundleSender.Result;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class DefaultTransferProcessRunner implements TransferProcessRunner {

  public Mono<Result> run(TransferProcess process, Flux<TransportBundle> data) {
    return process.bundleSender().send(process.deidentificationProvider().replaceIds(data));
  }
}
