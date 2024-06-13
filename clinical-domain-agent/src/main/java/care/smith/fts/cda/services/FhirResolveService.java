package care.smith.fts.cda.services;

import static com.google.common.base.Strings.emptyToNull;
import static java.util.Objects.requireNonNull;

import ca.uhn.fhir.context.FhirContext;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
public class FhirResolveService implements PatientIdResolver {

  private final FhirContext fhir;
  private final WebClient client;
  private final String identifierSystem;

  public FhirResolveService(String identifierSystem, WebClient client, FhirContext fhir) {
    this.identifierSystem = identifierSystem;
    this.client = client;
    this.fhir = fhir;
  }

  /**
   * Resolves the given <code>patientId</code> to the ID of the matching {@link Patient} resource
   * object in the FHIR server accessed with the rest configuration.
   *
   * @param patientId the patient ID (PID) to resolve
   * @return the ID of the FHIR resource
   */
  @Override
  public IIdType resolve(String patientId) {
    return this.resolveFromPatient(patientId).getIdElement();
  }

  private IBaseResource resolveFromPatient(String patientId) {
    requireNonNull(emptyToNull(patientId), "patientId must not be null or empty");
    Bundle patients = fetchPatientBundle(patientId);
    checkBundleNotEmpty(patients, patientId);
    checkSinglePatient(patients, patientId);
    return patients.getEntryFirstRep().getResource();
  }

  private Bundle fetchPatientBundle(String patientId) {
    return client
        .search()
        .forResource(Patient.class)
        .where(Patient.IDENTIFIER.exactly().systemAndValues(identifierSystem, patientId))
        .returnBundle(Bundle.class)
        .execute();
  }

  private void checkSinglePatient(Bundle patients, String patientId) {
    if (patients.getTotal() != 1 || patients.getEntry().size() != 1) {
      throw new IllegalStateException(
          "Received more then one result while resolving patient ID %s: %s"
              .formatted(patientId, fhir.newJsonParser().encodeResourceToString(patients)));
    }
  }

  private void checkBundleNotEmpty(Bundle patients, String patientId) {
    if (patients.getTotal() == 0 || patients.getEntry().isEmpty()) {
      throw new IllegalStateException(
          "Unable to resolve patient ID %s: %s"
              .formatted(patientId, fhir.newJsonParser().encodeResourceToString(patients)));
    }
  }
}
