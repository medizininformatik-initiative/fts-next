package care.smith.fts.api.cda;

import care.smith.fts.api.CohortEntry;
import care.smith.fts.api.TransferProcessStep;
import care.smith.fts.api.TransferProcessStepFactory;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import reactor.core.publisher.Flux;

public interface CohortSelector extends TransferProcessStep {

  /**
   * Selects the cohort of a transfer.
   *
   * @param identifiers the patients to select, or all patients if empty
   * @return consented patients, and patients excluded for a reason the user has to see
   */
  Flux<CohortEntry> selectCohort(@NotNull List<String> identifiers);

  interface Factory<C> extends TransferProcessStepFactory<CohortSelector, Config, C> {}

  record Config() {}
}
