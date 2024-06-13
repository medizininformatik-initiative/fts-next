package care.smith.fts.cda;

import org.hl7.fhir.r4.model.Bundle;
import reactor.core.publisher.Flux;

public interface TransferProcessRunner {
  Flux<R4TransferProcessRunner.Result> run(TransferProcess<Bundle> process);
}
