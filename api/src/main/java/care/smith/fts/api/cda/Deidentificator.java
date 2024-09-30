package care.smith.fts.api.cda;

import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.api.TransferProcessStep;
import care.smith.fts.api.TransferProcessStepFactory;
import care.smith.fts.api.TransportBundle;
import reactor.core.publisher.Mono;

public interface Deidentificator extends TransferProcessStep {

  Mono<TransportBundle> deidentify(ConsentedPatientBundle bundle);

  interface Factory<C> extends TransferProcessStepFactory<Deidentificator, Config, C> {}

  record Config() {}
}
