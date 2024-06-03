package care.smith.fts.api;

import org.hl7.fhir.instance.model.api.IBaseBundle;

public interface DeidentificationProvider {

  IBaseBundle deidentify(IBaseBundle b);

  interface Factory<C> extends StepFactory<DeidentificationProvider, Config, C> {}

  record Config() {}
}
