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
import static org.assertj.core.api.InstanceOfAssertFactories.type;

import care.smith.fts.api.CohortEntry;
import care.smith.fts.api.CohortEntry.Consented;
import care.smith.fts.api.CohortEntry.Excluded;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConsentedPatientExtractorTest {

  private static final String ANOTHER_PATIENT_SYSTEM = "http://another.com/patient";
  private static final Set<String> POLICIES_TO_CHECK = Set.of("POLICY_A", "POLICY_B");

  private static final ConsentedPatientExtractor EXTRACTOR =
      extractor(POLICIES_TO_CHECK, b -> getPatientIdentifier(PATIENT_IDENTIFIER_SYSTEM, b));

  @Test
  void cohortEntries() {
    var bundle1 = generateBundleWithConsent("12345", "POLICY_A", "POLICY_B");
    var bundle2 = generateBundleWithConsent("67890", "POLICY_A", "POLICY_B");
    var bundle3 = generateBundleWithConsent("99999", "POLICY_A"); // Missing POLICY_B

    var result = EXTRACTOR.cohortEntries(Stream.of(bundle1, bundle2, bundle3)).toList();

    assertThat(result).extracting(CohortEntry::identifier).containsExactly("12345", "67890");
  }

  @Test
  void cohortEntries_continuesAfterPatientWithUnreadableDate() {
    var unreadable =
        bundleWithProvisions(
            "12345",
            permittedProvisionComponent("POLICY_A"),
            parsedProvision("POLICY_B", "2024-02-23T10:00:00", "2054-01-31"));
    var readable = generateBundleWithConsent("67890", "POLICY_A", "POLICY_B");

    var result = EXTRACTOR.cohortEntries(Stream.of(unreadable, readable)).toList();

    assertThat(result)
        .satisfiesExactly(
            e -> assertThat(e).isInstanceOf(Excluded.class),
            e -> assertThat(e).isInstanceOf(Consented.class));
  }

  @Test
  void cohortEntries_withUnknownPolicies() {
    var bundle = generateBundleWithHospitalSystem("12345");
    var extractor =
        extractor(
            Set.of("UNKNOWN_POLICY"), b -> getPatientIdentifier(PATIENT_IDENTIFIER_SYSTEM, b));

    var result = extractor.cohortEntries(Stream.of(bundle)).toList();

    assertThat(result).isEmpty();
  }

  @Test
  void deniedProvisionDoesNotCompletePermittedPolicies() {
    var bundle =
        bundleWithProvisions(
            "12345", permittedProvisionComponent("POLICY_A"), deniedProvisionComponent("POLICY_B"));

    assertThat(EXTRACTOR.toCohortEntry(bundle)).isEmpty();
  }

  @Test
  void toCohortEntry_withAllPoliciesIsConsented() {
    var bundle = generateBundleWithConsent("12345", "POLICY_A", "POLICY_B");

    var result = EXTRACTOR.toCohortEntry(bundle);

    assertThat(result)
        .get()
        .asInstanceOf(type(Consented.class))
        .extracting(Consented::patient)
        .satisfies(
            p -> {
              assertThat(p.identifier()).isEqualTo("12345");
              assertThat(p.patientIdentifierSystem()).isEqualTo(PATIENT_IDENTIFIER_SYSTEM);
              assertThat(p.consentedPolicies().hasAllPolicies(POLICIES_TO_CHECK)).isTrue();
            });
  }

  @Test
  void toCohortEntry_withMissingPolicyIsNotReported() {
    var bundle = generateBundleWithConsent("12345", "POLICY_A");

    assertThat(EXTRACTOR.toCohortEntry(bundle)).isEmpty();
  }

  @Test
  void toCohortEntry_withNoPatientIsNotReported() {
    var bundle = generateBundleWithConsent("12345", "POLICY_A", "POLICY_B");
    var extractor = extractor(POLICIES_TO_CHECK, b -> Optional.empty());

    assertThat(extractor.toCohortEntry(bundle)).isEmpty();
  }

  @Test
  void toCohortEntry_requiredPolicyOnlyInProvisionWithoutStartIsExcluded() {
    var withoutPeriod = permittedProvisionComponent("POLICY_B").setPeriod(null);
    var bundle =
        bundleWithProvisions("12345", permittedProvisionComponent("POLICY_A"), withoutPeriod);

    var result = EXTRACTOR.toCohortEntry(bundle);

    assertThat(result)
        .contains(new Excluded("12345", "Consent for policy POLICY_B has no start date"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"2024-02-23T10:00:00|2054-01-31", "2024-02-23|2054-01-31T10:00:00"})
  void toCohortEntry_requiredPolicyOnlyInProvisionWithUnreadableDateIsExcluded(String dates) {
    var startAndEnd = dates.split("\\|");
    var unreadable = parsedProvision("POLICY_B", startAndEnd[0], startAndEnd[1]);
    var bundle = bundleWithProvisions("12345", permittedProvisionComponent("POLICY_A"), unreadable);

    var result = EXTRACTOR.toCohortEntry(bundle);

    assertThat(result)
        .contains(new Excluded("12345", "Consent for policy POLICY_B has an unreadable date"));
  }

  @Test
  void toCohortEntry_faultyProvisionOfConsentedPolicyDoesNotExclude() {
    var faultyA = permittedProvisionComponent("POLICY_A").setPeriod(null);
    var bundle = bundleWithProvisions("12345", permittedProvisionComponent("POLICY_A"), faultyA);

    assertThat(EXTRACTOR.toCohortEntry(bundle)).isEmpty();
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
