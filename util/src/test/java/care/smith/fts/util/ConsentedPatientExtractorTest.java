package care.smith.fts.util;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.ConsentedPatient;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ConsentedPatientExtractorTest {

  private static final String PATIENT_IDENTIFIER_SYSTEM = "http://hospital.com/patient";
  private static final String POLICY_SYSTEM = "http://hospital.com/policy";
  private static final Set<String> POLICIES_TO_CHECK = Set.of("POLICY_A", "POLICY_B");

  private static Bundle bundle1;
  private static Bundle bundle2;

  @BeforeAll
  static void setUp() {
    bundle1 = generateBundle();
    bundle2 = generateBundle();
  }

  private static Bundle generateBundle() {
    Patient patient = new Patient();
    Identifier identifier = new Identifier().setSystem(PATIENT_IDENTIFIER_SYSTEM).setValue("12345");
    patient.addIdentifier(identifier);

    Consent consent = new Consent();
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

  @Test
  void extractConsentedPatients() {
    Bundle outerBundle = new Bundle();
    outerBundle.addEntry().setResource(bundle1);
    outerBundle.addEntry().setResource(bundle2);

    Stream<ConsentedPatient> consentedPatients =
        ConsentedPatientExtractor.extractConsentedPatients(
            PATIENT_IDENTIFIER_SYSTEM, POLICY_SYSTEM, outerBundle, POLICIES_TO_CHECK);

    List<ConsentedPatient> result = consentedPatients.collect(Collectors.toList());
    assertThat(result).hasSize(2);
    assertThat(result.get(0).id()).isEqualTo("12345");
  }

  @Test
  void extractConsentedPatient() {
    Optional<ConsentedPatient> consentedPatient =
        ConsentedPatientExtractor.extractConsentedPatient(
            PATIENT_IDENTIFIER_SYSTEM, POLICY_SYSTEM, bundle1, POLICIES_TO_CHECK);

    assertThat(consentedPatient).isPresent();
    assertThat(consentedPatient.get().id()).isEqualTo("12345");
  }

  @Test
  void extractConsentedPatientWithUnknownPoliciesYieldsEmptyResult() {
    Optional<ConsentedPatient> consentedPatient =
        ConsentedPatientExtractor.extractConsentedPatient(
            PATIENT_IDENTIFIER_SYSTEM, POLICY_SYSTEM, bundle1, Set.of("Unknown Policy"));

    assertThat(consentedPatient).isEmpty();
  }

  @Test
  void hasAllPolicies() {
    boolean result =
        ConsentedPatientExtractor.hasAllPolicies(POLICY_SYSTEM, bundle1, POLICIES_TO_CHECK);

    assertThat(result).isTrue();
  }
}
