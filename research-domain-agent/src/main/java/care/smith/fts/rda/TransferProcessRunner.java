package care.smith.fts.rda;

import care.smith.fts.api.TransportBundle;
import reactor.core.publisher.Flux;

public interface TransferProcessRunner {
  Flux<Result> run(TransferProcess process, Flux<TransportBundle> data);

  record Result() {}
}
