package care.smith.fts.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;

class ConsentedPatientExtractorTest {

  private static final String HOSPITAL_PATIENT_SYSTEM = "http://hospital.com/patient";
  private static final String ANOTHER_PATIENT_SYSTEM = "http://another.com/patient";
  private static final String PATIENT_IDENTIFIER_SYSTEM = "http://hospital.com/patient";
  private static final String POLICY_SYSTEM = "http://hospital.com/policy";
  private static final Set<String> POLICIES_TO_CHECK = Set.of("POLICY_A", "POLICY_B");

  @Test
  void processConsentedPatients() {
    var bundle1 = generateBundleWithConsent("12345", "POLICY_A", "POLICY_B");
    var bundle2 = generateBundleWithConsent("67890", "POLICY_A", "POLICY_B");
    var bundle3 = generateBundleWithConsent("99999", "POLICY_A"); // Missing POLICY_B

    var result =
        ConsentedPatientExtractor.processConsentedPatients(
                PATIENT_IDENTIFIER_SYSTEM,
                POLICY_SYSTEM,
                Stream.of(bundle1, bundle2, bundle3),
                POLICIES_TO_CHECK,
                bundle -> getPatientId(bundle))
            .collect(Collectors.toList());

    assertThat(result).hasSize(2); // Only bundles with all policies should be included
    assertThat(result.get(0).id()).isEqualTo("12345");
    assertThat(result.get(0).patientIdentifierSystem()).isEqualTo(PATIENT_IDENTIFIER_SYSTEM);
    assertThat(result.get(1).id()).isEqualTo("67890");
    assertThat(result.get(1).patientIdentifierSystem()).isEqualTo(PATIENT_IDENTIFIER_SYSTEM);
  }

  @Test
  void processConsentedPatient_withAllPolicies() {
    var bundle = generateBundleWithConsent("12345", "POLICY_A", "POLICY_B");

    var result =
        ConsentedPatientExtractor.processConsentedPatient(
            PATIENT_IDENTIFIER_SYSTEM,
            POLICY_SYSTEM,
            bundle,
            POLICIES_TO_CHECK,
            b -> getPatientId(b));

    assertThat(result).isPresent();
    assertThat(result.get().id()).isEqualTo("12345");
    assertThat(result.get().patientIdentifierSystem()).isEqualTo(PATIENT_IDENTIFIER_SYSTEM);
    assertThat(result.get().consentedPolicies().hasAllPolicies(POLICIES_TO_CHECK)).isTrue();
  }

  @Test
  void processConsentedPatient_withMissingPolicies() {
    var bundle = generateBundleWithConsent("12345", "POLICY_A"); // Missing POLICY_B

    var result =
        ConsentedPatientExtractor.processConsentedPatient(
            PATIENT_IDENTIFIER_SYSTEM,
            POLICY_SYSTEM,
            bundle,
            POLICIES_TO_CHECK,
            b -> getPatientId(b));

    assertThat(result).isEmpty();
  }

  @Test
  void processConsentedPatient_withNoPatient() {
    var bundle = generateBundleWithConsent("12345", "POLICY_A", "POLICY_B");

    var result =
        ConsentedPatientExtractor.processConsentedPatient(
            PATIENT_IDENTIFIER_SYSTEM,
            POLICY_SYSTEM,
            bundle,
            POLICIES_TO_CHECK,
            b -> Optional.empty()); // Patient extractor returns empty

    assertThat(result).isEmpty();
  }

  @Test
  void hasAllPolicies_withAllRequired() {
    var bundle = generateBundleWithConsent("12345", "POLICY_A", "POLICY_B");

    var result = ConsentedPatientExtractor.hasAllPolicies(POLICY_SYSTEM, bundle, POLICIES_TO_CHECK);

    assertThat(result).isTrue();
  }

  @Test
  void hasAllPolicies_withMissingPolicy() {
    var bundle = generateBundleWithConsent("12345", "POLICY_A"); // Missing POLICY_B

    var result = ConsentedPatientExtractor.hasAllPolicies(POLICY_SYSTEM, bundle, POLICIES_TO_CHECK);

    assertThat(result).isFalse();
  }

  @Test
  void hasAllPolicies_withExtraPolicies() {
    var bundle = generateBundleWithConsent("12345", "POLICY_A", "POLICY_B", "POLICY_C");

    var result = ConsentedPatientExtractor.hasAllPolicies(POLICY_SYSTEM, bundle, POLICIES_TO_CHECK);

    assertThat(result).isTrue(); // Should still return true when extra policies are present
  }

  @Test
  void getConsentedPatients_withUnknownPolicies() {
    var bundle = generateBundleWithHospitalSystem("12345");

    var consentedPatients =
        ConsentedPatientExtractor.processConsentedPatients(
            HOSPITAL_PATIENT_SYSTEM,
            POLICY_SYSTEM,
            Stream.of(bundle),
            Set.of("UNKNOWN_POLICY"),
            bundle1 ->
                ConsentedPatientExtractor.getPatientIdentifier(HOSPITAL_PATIENT_SYSTEM, bundle1));

    var result = consentedPatients.collect(Collectors.toList());
    assertThat(result).isEmpty(); // No patients should match
  }

  @Test
  void getConsentedPolicies() {
    var bundle = generateBundleWithConsent("12345", "POLICY_A", "POLICY_B", "POLICY_C");

    var result =
        ConsentedPatientExtractor.getConsentedPolicies(POLICY_SYSTEM, bundle, POLICIES_TO_CHECK);

    assertThat(result.hasAllPolicies(Set.of("POLICY_A", "POLICY_B"))).isTrue();
    assertThat(result.hasAllPolicies(Set.of("POLICY_A", "POLICY_B", "POLICY_C")))
        .isFalse(); // POLICY_C not in policiesToCheck
  }

  @Test
  void getPermitProvisionsStream() {
    var bundle = generateBundleWithConsent("12345", "POLICY_A", "POLICY_B");

    var provisions =
        ConsentedPatientExtractor.getPermitProvisionsStream(bundle).collect(Collectors.toList());

    assertThat(provisions).hasSize(2); // Two permit provisions for POLICY_A and POLICY_B
  }

  @Test
  void getConsentedPoliciesFromProvision() {
    var provision =
        new Consent.ProvisionComponent()
            .setType(Consent.ConsentProvisionType.PERMIT)
            .setCode(
                List.of(
                    new CodeableConcept()
                        .addCoding(new Coding().setSystem(POLICY_SYSTEM).setCode("POLICY_A"))))
            .setPeriod(new Period().setStart(new Date(0)).setEnd(new Date(1)));

    var result =
        ConsentedPatientExtractor.getConsentedPoliciesFromProvision(
            POLICY_SYSTEM, provision, Set.of("POLICY_A", "POLICY_B"));

    assertThat(result.hasAllPolicies(Set.of("POLICY_A"))).isTrue();
    assertThat(result.hasAllPolicies(Set.of("POLICY_A", "POLICY_B"))).isFalse();
  }

  @Test
  void extractPolicyFromCodeableConcept() {
    var concept =
        new CodeableConcept()
            .addCoding(new Coding().setSystem(POLICY_SYSTEM).setCode("POLICY_A"))
            .addCoding(new Coding().setSystem("http://other.system").setCode("OTHER_POLICY"))
            .addCoding(new Coding().setSystem(POLICY_SYSTEM).setCode("POLICY_B"));

    var policies =
        ConsentedPatientExtractor.extractPolicyFromCodeableConcept(
                POLICY_SYSTEM, Set.of("POLICY_A", "POLICY_B", "POLICY_C"), concept)
            .collect(Collectors.toSet());

    assertThat(policies).containsExactlyInAnyOrder("POLICY_A", "POLICY_B");
    assertThat(policies).doesNotContain("OTHER_POLICY"); // Wrong system
    assertThat(policies).doesNotContain("POLICY_C"); // Not in concept
  }

  private static Bundle generateBundleWithConsent(String patientId, String... policies) {
    var patient = new Patient();
    patient.addIdentifier(
        new Identifier().setSystem(PATIENT_IDENTIFIER_SYSTEM).setValue(patientId));

    var consent = new Consent();
    var mainProvision = new Consent.ProvisionComponent().setType(Consent.ConsentProvisionType.DENY);

    for (String policy : policies) {
      mainProvision.addProvision(permittedProvisionComponent(policy));
    }
    consent.setProvision(mainProvision);

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

  private static Optional<String> getPatientId(Bundle bundle) {
    return bundle.getEntry().stream()
        .map(Bundle.BundleEntryComponent::getResource)
        .filter(Patient.class::isInstance)
        .map(Patient.class::cast)
        .findFirst()
        .flatMap(
            p ->
                p.getIdentifier().stream()
                    .filter(id -> PATIENT_IDENTIFIER_SYSTEM.equals(id.getSystem()))
                    .map(Identifier::getValue)
                    .findFirst());
  }

  @Test
  void getPatientIdentifier_withMatchingSystem() {
    var bundle = generateBundleWithHospitalSystem("12345");

    var result = ConsentedPatientExtractor.getPatientIdentifier(HOSPITAL_PATIENT_SYSTEM, bundle);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo("12345");
  }

  @Test
  void getPatientIdentifier_withNonMatchingSystem() {
    var bundle = generateBundleWithHospitalSystem("12345");

    var result = ConsentedPatientExtractor.getPatientIdentifier(ANOTHER_PATIENT_SYSTEM, bundle);

    assertThat(result).isEmpty();
  }

  @Test
  void getPatientIdentifier_withMultipleIdentifiers() {
    var bundle = generateBundleWithMultipleIdentifiers("12345");

    var result = ConsentedPatientExtractor.getPatientIdentifier(HOSPITAL_PATIENT_SYSTEM, bundle);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo("12345");
  }

  @Test
  void getPatientIdentifier_withoutPatient() {
    var bundle = new Bundle(); // Empty bundle

    var result = ConsentedPatientExtractor.getPatientIdentifier(HOSPITAL_PATIENT_SYSTEM, bundle);

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
}
