package care.smith.fts.api.cda;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.api.TransferProcessStep;
import care.smith.fts.api.TransferProcessStepFactory;
import reactor.core.publisher.Flux;

public interface DataSelector extends TransferProcessStep {

  Flux<ConsentedPatientBundle> select(ConsentedPatient consentedPatient);

  interface Factory<C> extends TransferProcessStepFactory<DataSelector, Config, C> {}

  record Config(boolean ignoreConsent) {}
}
