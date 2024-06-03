package care.smith.fts.api;

import java.util.List;

public interface CohortSelector {

  List<ConsentedPatient> selectCohort();

  interface Factory<C> extends StepFactory<CohortSelector, Config, C> {}

  record Config() {}
}
