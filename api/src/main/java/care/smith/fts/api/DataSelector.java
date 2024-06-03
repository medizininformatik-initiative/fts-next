package care.smith.fts.api;

import jakarta.validation.constraints.NotNull;
import org.hl7.fhir.instance.model.api.IBaseBundle;

public interface DataSelector {

  IBaseBundle select(ConsentedPatient consentedPatient);

  interface Factory<C> extends StepFactory<DataSelector, Config, C> {}

  record Config(
      @NotNull ResolvePatientConfig resolvePatient,
      @NotNull AdditionalFilterConfig additionalFilter) {}

  record ResolvePatientConfig(boolean replaceId, String patientIdentifierSystem) {}

  record AdditionalFilterConfig(Object none, Object clinicalDate, Object encounter) {}
}
