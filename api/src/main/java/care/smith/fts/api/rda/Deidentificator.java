package care.smith.fts.api.rda;

import care.smith.fts.api.TransferProcessStep;
import care.smith.fts.api.TransferProcessStepFactory;
import care.smith.fts.api.TransportBundle;
import org.hl7.fhir.r4.model.Bundle;
import reactor.core.publisher.Mono;

public interface Deidentificator extends TransferProcessStep {

  Mono<Bundle> deidentify(TransportBundle bundle);

  interface Factory<C> extends TransferProcessStepFactory<Deidentificator, Config, C> {}

  record Config() {}
}
