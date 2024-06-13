package care.smith.fts.api;

import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import reactor.core.publisher.Flux;

/**
 * @param <B> FHIR Type of the PatientBundle type
 */
public interface DeidentificationProvider<In, Out> {

  Flux<Out> deidentify(Flux<In> inFlux);

  interface Factory<In, Out, C> extends StepFactory<DeidentificationProvider<In, Out>, Config, C> {}

  record Config() {}
}
