package care.smith.fts.api;

import jakarta.validation.constraints.NotNull;
import org.hl7.fhir.instance.model.api.IBaseBundle;

public interface DataSelector {

  IBaseBundle select(ConsentedPatient consentedPatient);

  interface Factory<C> extends StepFactory<DataSelector, Config, C> {}

  record Config(boolean ignoreConsent, @NotNull AdditionalFilterConfig additionalFilter) {}

  record AdditionalFilterConfig(Object none, Object clinicalDate, Object encounter) {}
}
