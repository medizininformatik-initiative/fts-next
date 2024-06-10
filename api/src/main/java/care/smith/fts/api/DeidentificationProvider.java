package care.smith.fts.api;

import org.hl7.fhir.instance.model.api.IBaseBundle;

public interface DeidentificationProvider<B extends IBaseBundle> {

  B deidentify(B b);

  interface Factory<B extends IBaseBundle, C>
      extends StepFactory<DeidentificationProvider<B>, Config, C> {}

  record Config() {}
}
