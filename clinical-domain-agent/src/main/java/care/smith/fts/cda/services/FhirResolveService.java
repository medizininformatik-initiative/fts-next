package care.smith.fts.cda.services;

import static com.google.common.base.Strings.emptyToNull;
import static java.util.Objects.requireNonNull;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;

@Slf4j
public class FhirResolveService implements PatientIdResolver {

  private final IGenericClient client;
  private final String identifierSystem;

  public FhirResolveService(String identifierSystem, IGenericClient client) {
    this.identifierSystem = identifierSystem;
    this.client = client;
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
    IIdType idElement = this.resolveFromPatient(patientId).getIdElement();
    if (idElement.hasResourceType()) {
      return idElement;
    } else {
      return idElement.withResourceType("Patient");
    }
  }

  private IBaseResource resolveFromPatient(String patientId) {
    requireNonNull(emptyToNull(patientId), "patientId must not be empty");
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

  private static void checkSinglePatient(Bundle patients, String patientId) {
    if (patients.getTotal() != 1 || patients.getEntry().size() != 1) {
      throw new IllegalStateException(
          "Received more then one result while resolving patient ID %s :%s%s"
              .formatted(
                  patientId,
                  System.lineSeparator(),
                  FhirContext.forR4().newJsonParser().encodeResourceToString(patients)));
    }
  }

  private static void checkBundleNotEmpty(Bundle patients, String patientId) {
    if (patients.getTotal() == 0 || patients.getEntry().isEmpty()) {
      throw new IllegalStateException(
          "Unable to resolve patient ID %s :%s%s"
              .formatted(
                  patientId,
                  System.lineSeparator(),
                  FhirContext.forR4().newJsonParser().encodeResourceToString(patients)));
    }
  }
}
