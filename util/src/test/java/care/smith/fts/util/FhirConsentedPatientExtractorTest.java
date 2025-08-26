package care.smith.fts.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;

class FhirConsentedPatientExtractorTest {

  private static final String HOSPITAL_PATIENT_SYSTEM = "http://hospital.com/patient";
  private static final String ANOTHER_PATIENT_SYSTEM = "http://another.com/patient";
  private static final String POLICY_SYSTEM = "http://hospital.com/policy";
  private static final Set<String> POLICIES_TO_CHECK = Set.of("POLICY_A", "POLICY_B");

  @Test
  void getConsentedPatients() {
    var bundle1 = generateBundleWithHospitalSystem("12345");
    var bundle2 = generateBundleWithHospitalSystem("67890");

    var consentedPatients =
        FhirConsentedPatientExtractor.getConsentedPatients(
            HOSPITAL_PATIENT_SYSTEM, POLICY_SYSTEM, Stream.of(bundle1, bundle2), POLICIES_TO_CHECK);

    var result = consentedPatients.collect(Collectors.toList());
    assertThat(result).hasSize(2);
    assertThat(result.get(0).id()).isEqualTo("12345");
    assertThat(result.get(0).patientIdentifierSystem()).isEqualTo(HOSPITAL_PATIENT_SYSTEM);
    assertThat(result.get(1).id()).isEqualTo("67890");
    assertThat(result.get(1).patientIdentifierSystem()).isEqualTo(HOSPITAL_PATIENT_SYSTEM);
  }

  @Test
  void getConsentedPatients_withMissingPatientIdentifier() {
    var bundle1 = generateBundleWithHospitalSystem("12345");
    var bundle2 = generateBundleWithDifferentSystem("67890"); // Uses ANOTHER_PATIENT_SYSTEM

    var consentedPatients =
        FhirConsentedPatientExtractor.getConsentedPatients(
            HOSPITAL_PATIENT_SYSTEM, POLICY_SYSTEM, Stream.of(bundle1, bundle2), POLICIES_TO_CHECK);

    var result = consentedPatients.collect(Collectors.toList());
    assertThat(result).hasSize(1); // Only bundle1 should match
    assertThat(result.get(0).id()).isEqualTo("12345");
    assertThat(result.get(0).patientIdentifierSystem()).isEqualTo(HOSPITAL_PATIENT_SYSTEM);
  }

  @Test
  void getConsentedPatients_withMixedIdentifierSystems() {
    var bundle = generateBundleWithMultipleIdentifiers("12345");

    var consentedPatients =
        FhirConsentedPatientExtractor.getConsentedPatients(
            HOSPITAL_PATIENT_SYSTEM, POLICY_SYSTEM, Stream.of(bundle), POLICIES_TO_CHECK);

    var result = consentedPatients.collect(Collectors.toList());
    assertThat(result).hasSize(1);
    assertThat(result.get(0).id()).isEqualTo("12345"); // Should use the hospital system ID
    assertThat(result.get(0).patientIdentifierSystem()).isEqualTo(HOSPITAL_PATIENT_SYSTEM);
  }

  @Test
  void getConsentedPatients_withUnknownPolicies() {
    var bundle = generateBundleWithHospitalSystem("12345");

    var consentedPatients =
        FhirConsentedPatientExtractor.getConsentedPatients(
            HOSPITAL_PATIENT_SYSTEM, POLICY_SYSTEM, Stream.of(bundle), Set.of("UNKNOWN_POLICY"));

    var result = consentedPatients.collect(Collectors.toList());
    assertThat(result).isEmpty(); // No patients should match
  }

  @Test
  void getPatientIdentifier_withMatchingSystem() {
    var bundle = generateBundleWithHospitalSystem("12345");

    var result =
        FhirConsentedPatientExtractor.getPatientIdentifier(HOSPITAL_PATIENT_SYSTEM, bundle);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo("12345");
  }

  @Test
  void getPatientIdentifier_withNonMatchingSystem() {
    var bundle = generateBundleWithHospitalSystem("12345");

    var result = FhirConsentedPatientExtractor.getPatientIdentifier(ANOTHER_PATIENT_SYSTEM, bundle);

    assertThat(result).isEmpty();
  }

  @Test
  void getPatientIdentifier_withMultipleIdentifiers() {
    var bundle = generateBundleWithMultipleIdentifiers("12345");

    var result =
        FhirConsentedPatientExtractor.getPatientIdentifier(HOSPITAL_PATIENT_SYSTEM, bundle);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo("12345");
  }

  @Test
  void getPatientIdentifier_withoutPatient() {
    var bundle = new Bundle(); // Empty bundle

    var result =
        FhirConsentedPatientExtractor.getPatientIdentifier(HOSPITAL_PATIENT_SYSTEM, bundle);

    assertThat(result).isEmpty();
  }

  private static Bundle generateBundleWithHospitalSystem(String id) {
    var patient = new Patient();
    var identifier = new Identifier().setSystem(HOSPITAL_PATIENT_SYSTEM).setValue(id);
    patient.addIdentifier(identifier);

    var consent = new Consent();
    consent.setProvision(
        new Consent.ProvisionComponent()
            .setType(Consent.ConsentProvisionType.DENY)
            .addProvision(permittedProvisionComponent("POLICY_A"))
            .addProvision(permittedProvisionComponent("POLICY_B")));

    var bundle = new Bundle();
    bundle.addEntry().setResource(patient);
    bundle.addEntry().setResource(consent);
    return bundle;
  }

  private static Bundle generateBundleWithDifferentSystem(String id) {
    var patient = new Patient();
    var identifier = new Identifier().setSystem(ANOTHER_PATIENT_SYSTEM).setValue(id);
    patient.addIdentifier(identifier);

    var consent = new Consent();
    consent.setProvision(
        new Consent.ProvisionComponent()
            .setType(Consent.ConsentProvisionType.DENY)
            .addProvision(permittedProvisionComponent("POLICY_A"))
            .addProvision(permittedProvisionComponent("POLICY_B")));

    var bundle = new Bundle();
    bundle.addEntry().setResource(patient);
    bundle.addEntry().setResource(consent);
    return bundle;
  }

  private static Bundle generateBundleWithMultipleIdentifiers(String hospitalId) {
    var patient = new Patient();
    patient.addIdentifier(new Identifier().setSystem(HOSPITAL_PATIENT_SYSTEM).setValue(hospitalId));
    patient.addIdentifier(
        new Identifier().setSystem(ANOTHER_PATIENT_SYSTEM).setValue("OTHER_" + hospitalId));

    var consent = new Consent();
    consent.setProvision(
        new Consent.ProvisionComponent()
            .setType(Consent.ConsentProvisionType.DENY)
            .addProvision(permittedProvisionComponent("POLICY_A"))
            .addProvision(permittedProvisionComponent("POLICY_B")));

    var bundle = new Bundle();
    bundle.addEntry().setResource(patient);
    bundle.addEntry().setResource(consent);
    return bundle;
  }

  private static Consent.ProvisionComponent permittedProvisionComponent(String policy) {
    return new Consent.ProvisionComponent()
        .setType(Consent.ConsentProvisionType.PERMIT)
        .setCode(
            List.of(
                new CodeableConcept()
                    .addCoding(new Coding().setSystem(POLICY_SYSTEM).setCode(policy))))
        .setPeriod(new Period().setStart(new Date(0)).setEnd(new Date(1)));
  }
}
