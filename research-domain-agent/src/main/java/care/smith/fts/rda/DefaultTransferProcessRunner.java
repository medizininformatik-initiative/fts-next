package care.smith.fts.rda;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.rda.TransferProcessRunner.Result;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class DefaultTransferProcessRunner implements TransferProcessRunner {

  public Flux<Result> run(TransferProcess process, Flux<TransportBundle> data) {
    var pseudomized = process.deidentificationProvider().replaceIds(data);
    return Flux.from(process.bundleSender().send(pseudomized)).map(i -> new Result());
  }
}
