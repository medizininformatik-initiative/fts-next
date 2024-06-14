package care.smith.fts.rda;

import care.smith.fts.api.TransportBundle;
import java.util.concurrent.ForkJoinPool;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class R4TransferProcessRunner {

  // TODO use pool
  private final ForkJoinPool pool;

  public R4TransferProcessRunner(@Qualifier("transferProcessPool") ForkJoinPool pool) {
    this.pool = pool;
  }

  public Flux<Result> run(TransferProcess<Bundle> process, Flux<TransportBundle<Bundle>> data) {
    Flux<Bundle> deidentified = process.deidentificationProvider().deidentify(data);
    return Flux.from(process.bundleSender().send(deidentified)).map(i -> new Result());
  }

  public record Result() {}
}
