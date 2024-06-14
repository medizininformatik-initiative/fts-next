package care.smith.fts.api.cda;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.Step;
import care.smith.fts.api.StepFactory;
import reactor.core.publisher.Flux;

public interface CohortSelector extends Step {

  Flux<ConsentedPatient> selectCohort();

  interface Factory<C> extends StepFactory<CohortSelector, Config, C> {}

  record Config() {}
}
