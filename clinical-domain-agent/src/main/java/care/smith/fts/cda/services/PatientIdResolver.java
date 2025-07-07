package care.smith.fts.cda.services;

import care.smith.fts.api.ConsentedPatient;
import org.hl7.fhir.instance.model.api.IIdType;
import reactor.core.publisher.Mono;

public interface PatientIdResolver {
  Mono<IIdType> resolve(ConsentedPatient patient);
}
