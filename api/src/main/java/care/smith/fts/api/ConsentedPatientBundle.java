package care.smith.fts.api;

import org.hl7.fhir.r4.model.Bundle;

/**
 * A bundle of FHIR resources for a consented patient.
 *
 * @param bundle the FHIR bundle containing patient resources
 * @param consentedPatient the consented patient
 * @param patientResourceId the FHIR resource ID of the Patient (e.g., "DGXCRR3SDVNIEB2R"), which
 *     may differ from the patient identifier. This is needed for compartment membership checking
 *     because resource references use the resource ID, not the patient identifier.
 */
public record ConsentedPatientBundle(
    Bundle bundle, ConsentedPatient consentedPatient, String patientResourceId) {}
