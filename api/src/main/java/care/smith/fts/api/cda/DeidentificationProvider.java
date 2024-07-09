package care.smith.fts.api.cda;

import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.api.TransferProcessStep;
import care.smith.fts.api.TransferProcessStepFactory;
import care.smith.fts.api.TransportBundle;
import reactor.core.publisher.Flux;

public interface DeidentificationProvider extends TransferProcessStep {

  Flux<TransportBundle> deidentify(Flux<ConsentedPatientBundle> inFlux);

  interface Factory<C> extends TransferProcessStepFactory<DeidentificationProvider, Config, C> {}

  record Config() {}
}
