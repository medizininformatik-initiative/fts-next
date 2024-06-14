package care.smith.fts.api.cda;

import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.api.Step;
import care.smith.fts.api.StepFactory;
import care.smith.fts.api.TransportBundle;
import reactor.core.publisher.Flux;

public interface DeidentificationProvider extends Step {

  Flux<TransportBundle> deidentify(Flux<ConsentedPatientBundle> inFlux);

  interface Factory<C> extends StepFactory<DeidentificationProvider, Config, C> {}

  record Config() {}
}
