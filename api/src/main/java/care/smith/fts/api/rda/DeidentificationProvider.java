package care.smith.fts.api.rda;

import care.smith.fts.api.Step;
import care.smith.fts.api.StepFactory;
import care.smith.fts.api.TransportBundle;
import org.hl7.fhir.r4.model.Bundle;
import reactor.core.publisher.Flux;

public interface DeidentificationProvider extends Step {

  Flux<Bundle> deidentify(Flux<TransportBundle> inFlux);

  interface Factory<C> extends StepFactory<DeidentificationProvider, Config, C> {}

  record Config() {}
}