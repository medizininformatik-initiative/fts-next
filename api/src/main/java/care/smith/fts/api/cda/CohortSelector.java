package care.smith.fts.api.cda;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.TransferProcessStep;
import care.smith.fts.api.TransferProcessStepFactory;
import reactor.core.publisher.Flux;

public interface CohortSelector extends TransferProcessStep {

  Flux<ConsentedPatient> selectCohort();

  interface Factory<C> extends TransferProcessStepFactory<CohortSelector, Config, C> {}

  record Config() {}
}
