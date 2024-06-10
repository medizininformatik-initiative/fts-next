package care.smith.fts.api;

import jakarta.validation.constraints.NotNull;
import org.hl7.fhir.instance.model.api.IBaseBundle;

public interface DataSelector<B extends IBaseBundle> {

  B select(ConsentedPatient consentedPatient);

  interface Factory<B extends IBaseBundle, C> extends StepFactory<DataSelector<B>, Config, C> {}

  record Config(boolean ignoreConsent, @NotNull AdditionalFilterConfig additionalFilter) {}

  record AdditionalFilterConfig(Object none, Object clinicalDate, Object encounter) {}
}
