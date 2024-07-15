package care.smith.fts.cda.services;

import org.hl7.fhir.instance.model.api.IIdType;
import reactor.core.publisher.Mono;

public interface PatientIdResolver {
  Mono<IIdType> resolve(String patientId);
}
