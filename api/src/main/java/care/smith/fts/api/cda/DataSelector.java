package care.smith.fts.api.cda;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.Step;
import care.smith.fts.api.StepFactory;
import jakarta.validation.constraints.NotNull;
import org.hl7.fhir.r4.model.Bundle;
import reactor.core.publisher.Flux;

public interface DataSelector extends Step {

  Flux<Bundle> select(ConsentedPatient consentedPatient);

  interface Factory<C> extends StepFactory<DataSelector, Config, C> {}

  record Config(boolean ignoreConsent, @NotNull AdditionalFilterConfig additionalFilter) {}

  record AdditionalFilterConfig(Object none, Object clinicalDate, Object encounter) {}
}
