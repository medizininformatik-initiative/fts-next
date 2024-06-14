package care.smith.fts.cda;

import reactor.core.publisher.Flux;

public interface TransferProcessRunner {
  Flux<DefaultTransferProcessRunner.Result> run(TransferProcess process);
}
