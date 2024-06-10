package care.smith.fts.api;

import org.hl7.fhir.instance.model.api.IBaseResource;

import java.io.IOException;

public interface DeidentificationProvider<B extends IBaseResource> {

  B deidentify(B b, ConsentedPatient patient) throws IOException;

  interface Factory<B extends IBaseResource, C>
      extends StepFactory<DeidentificationProvider<B>, Config, C> {}

  record Config() {}
}
