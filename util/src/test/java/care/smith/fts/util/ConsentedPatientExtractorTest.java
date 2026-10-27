package care.smith.fts.util;

import static care.smith.fts.util.ConsentBundles.PATIENT_IDENTIFIER_SYSTEM;
import static care.smith.fts.util.ConsentBundles.POLICY_SYSTEM;
import static care.smith.fts.util.ConsentBundles.bundleWithProvisions;
import static care.smith.fts.util.ConsentBundles.deniedProvisionComponent;
import static care.smith.fts.util.ConsentBundles.generateBundleWithConsent;
import static care.smith.fts.util.ConsentBundles.parsedProvision;
import static care.smith.fts.util.ConsentBundles.permittedProvisionComponent;
import static care.smith.fts.util.ConsentedPatientExtractor.getPatientIdentifier;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.ConsentedPatient;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Consent.ConsentState;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

class ConsentedPatientExtractorTest {

  private static final String ANOTHER_PATIENT_SYSTEM = "http://another.com/patient";
  private static final Set<String> POLICIES_TO_CHECK = Set.of("POLICY_A", "POLICY_B");

  private static final ConsentedPatientExtractor EXTRACTOR =
      extractor(POLICIES_TO_CHECK, b -> getPatientIdentifier(PATIENT_IDENTIFIER_SYSTEM, b));

  @Test
  void consentedPatients() {
    var bundle1 = generateBundleWithConsent("12345", "POLICY_A", "POLICY_B");
    var bundle2 = generateBundleWithConsent("67890", "POLICY_A", "POLICY_B");
    var bundle3 = generateBundleWithConsent("99999", "POLICY_A"); // Missing POLICY_B

    var result = EXTRACTOR.consentedPatients(Stream.of(bundle1, bundle2, bundle3)).toList();

    assertThat(result).hasSize(2); // Only bundles with all policies should be included
    assertThat(result.get(0).identifier()).isEqualTo("12345");
    assertThat(result.get(0).patientIdentifierSystem()).isEqualTo(PATIENT_IDENTIFIER_SYSTEM);
    assertThat(result.get(1).identifier()).isEqualTo("67890");
    assertThat(result.get(1).patientIdentifierSystem()).isEqualTo(PATIENT_IDENTIFIER_SYSTEM);
  }

  @Test
  void consentedPatients_continuesAfterPatientWithUnreadableDate() {
    var unreadable =
        bundleWithProvisions(
            "12345",
            permittedProvisionComponent("POLICY_A"),
            parsedProvision("POLICY_B", "2024-02-23T10:00:00", "2054-01-31"));
    var readable = generateBundleWithConsent("67890", "POLICY_A", "POLICY_B");

    var result = EXTRACTOR.consentedPatients(Stream.of(unreadable, readable)).toList();

    assertThat(result).extracting(ConsentedPatient::identifier).containsExactly("67890");
  }

  @Test
  void consentedPatients_withUnknownPolicies() {
    var bundle = generateBundleWithHospitalSystem("12345");
    var extractor =
        extractor(
            Set.of("UNKNOWN_POLICY"), b -> getPatientIdentifier(PATIENT_IDENTIFIER_SYSTEM, b));

    var result = extractor.consentedPatients(Stream.of(bundle)).toList();

    assertThat(result).isEmpty();
  }

  @Test
  void consentedPatient_withAllPolicies() {
    var bundle = generateBundleWithConsent("12345", "POLICY_A", "POLICY_B");

    var result = EXTRACTOR.consentedPatient(bundle);

    assertThat(result).isPresent();
    assertThat(result.get().identifier()).isEqualTo("12345");
    assertThat(result.get().patientIdentifierSystem()).isEqualTo(PATIENT_IDENTIFIER_SYSTEM);
    assertThat(result.get().consentedPolicies().hasAllPolicies(POLICIES_TO_CHECK)).isTrue();
  }

  @Test
  void consentedPatient_withMissingPolicies() {
    var bundle = generateBundleWithConsent("12345", "POLICY_A"); // Missing POLICY_B

    assertThat(EXTRACTOR.consentedPatient(bundle)).isEmpty();
  }

  @Test
  void consentedPatient_withNoPatient() {
    var bundle = generateBundleWithConsent("12345", "POLICY_A", "POLICY_B");
    var extractor = extractor(POLICIES_TO_CHECK, b -> Optional.empty());

    assertThat(extractor.consentedPatient(bundle)).isEmpty();
  }

  @Test
  void deniedProvisionDoesNotCompletePermittedPolicies() {
    var bundle =
        bundleWithProvisions(
            "12345", permittedProvisionComponent("POLICY_A"), deniedProvisionComponent("POLICY_B"));

    assertThat(EXTRACTOR.consentedPatient(bundle)).isEmpty();
  }

  @Test
  void getPatientIdentifier_withMatchingSystem() {
    var bundle = generateBundleWithHospitalSystem("12345");

    var result = getPatientIdentifier(PATIENT_IDENTIFIER_SYSTEM, bundle);

    assertThat(result).contains("12345");
  }

  @Test
  void getPatientIdentifier_withNonMatchingSystem() {
    var bundle = generateBundleWithHospitalSystem("12345");

    var result = getPatientIdentifier(ANOTHER_PATIENT_SYSTEM, bundle);

    assertThat(result).isEmpty();
  }

  @Test
  void getPatientIdentifier_withMultipleIdentifiers() {
    var bundle = generateBundleWithMultipleIdentifiers("12345");

    var result = getPatientIdentifier(PATIENT_IDENTIFIER_SYSTEM, bundle);

    assertThat(result).contains("12345");
  }

  @Test
  void getPatientIdentifier_withoutPatient() {
    var result = getPatientIdentifier(PATIENT_IDENTIFIER_SYSTEM, new Bundle());

    assertThat(result).isEmpty();
  }

  private static ConsentedPatientExtractor extractor(
      Set<String> policies, Function<Bundle, Optional<String>> idExtractor) {
    return new ConsentedPatientExtractor(
        PATIENT_IDENTIFIER_SYSTEM, new RequiredPolicies(POLICY_SYSTEM, policies), idExtractor);
  }

  private static Bundle generateBundleWithHospitalSystem(String id) {
    var patient = new Patient();
    patient.addIdentifier(new Identifier().setSystem(PATIENT_IDENTIFIER_SYSTEM).setValue(id));
    return bundleWithConsentOfPolicyAAndB(patient);
  }

  private static Bundle generateBundleWithMultipleIdentifiers(String hospitalId) {
    var patient = new Patient();
    patient.addIdentifier(
        new Identifier().setSystem(PATIENT_IDENTIFIER_SYSTEM).setValue(hospitalId));
    patient.addIdentifier(
        new Identifier().setSystem(ANOTHER_PATIENT_SYSTEM).setValue("OTHER_" + hospitalId));
    return bundleWithConsentOfPolicyAAndB(patient);
  }

  private static Bundle bundleWithConsentOfPolicyAAndB(Patient patient) {
    var consent = new Consent().setStatus(ConsentState.ACTIVE);
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
}
