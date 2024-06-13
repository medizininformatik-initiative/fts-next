package care.smith.fts.api;

import reactor.core.publisher.Flux;

public interface CohortSelector {

  Flux<ConsentedPatient> selectCohort();

  interface Factory<C> extends StepFactory<CohortSelector, Config, C> {}

  record Config() {}
}
