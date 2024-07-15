package care.smith.fts.api.cda;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.TransferProcessStep;
import care.smith.fts.api.TransferProcessStepFactory;
import jakarta.validation.constraints.NotNull;
import org.hl7.fhir.r4.model.Bundle;
import reactor.core.publisher.Flux;

public interface DataSelector extends TransferProcessStep {

  Flux<Bundle> select(ConsentedPatient consentedPatient);

  interface Factory<C> extends TransferProcessStepFactory<DataSelector, Config, C> {}

  record Config(boolean ignoreConsent, @NotNull AdditionalFilterConfig additionalFilter) {}

  record AdditionalFilterConfig(Object none, Object clinicalDate, Object encounter) {}
}
