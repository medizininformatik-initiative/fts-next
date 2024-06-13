package care.smith.fts.api;

import org.hl7.fhir.instance.model.api.IBaseResource;
import reactor.core.publisher.Flux;

/**
 * @param <B> FHIR Type of the PatientBundle type
 */
public interface DeidentificationProvider<B extends IBaseResource> {

  Flux<B> deidentify(Flux<B> patientBundle, ConsentedPatient patient);

  interface Factory<B extends IBaseResource, C>
      extends StepFactory<DeidentificationProvider<B>, Config, C> {}

  record Config() {}
}
