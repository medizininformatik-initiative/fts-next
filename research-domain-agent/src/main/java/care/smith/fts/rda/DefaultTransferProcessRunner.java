package care.smith.fts.rda;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.rda.TransferProcessRunner.Result;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class DefaultTransferProcessRunner {

  public Flux<Result> run(TransferProcess process, Flux<TransportBundle> data) {
    Flux<Bundle> deidentified = process.deidentificationProvider().replaceIds(data);
    return Flux.from(process.bundleSender().send(deidentified)).map(i -> new Result());
  }

}
