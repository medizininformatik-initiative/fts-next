package care.smith.fts.cda.services;

import org.hl7.fhir.instance.model.api.IIdType;

public interface PatientIdResolver {
  IIdType resolve(String patientId);
}
