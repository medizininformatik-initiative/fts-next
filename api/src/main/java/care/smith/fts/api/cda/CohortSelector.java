package care.smith.fts.api.cda;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.TransferProcessStep;
import care.smith.fts.api.TransferProcessStepFactory;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import reactor.core.publisher.Flux;

public interface CohortSelector extends TransferProcessStep {

  Flux<ConsentedPatient> selectCohort(@NotNull List<String> identifiers);

  interface Factory<C> extends TransferProcessStepFactory<CohortSelector, Config, C> {}

  record Config() {}
}
