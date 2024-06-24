package care.smith.fts.rda;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.rda.BundleSender.Result;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TransferProcessRunner {
  Mono<Result> run(TransferProcess process, Flux<TransportBundle> data);
}
