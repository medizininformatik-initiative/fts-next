package care.smith.fts.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.Test;

class ConsentedPatientExtractorTest {

  private static final String PATIENT_IDENTIFIER_SYSTEM = "http://hospital.com/patient";
  private static final String POLICY_SYSTEM = "http://hospital.com/policy";
  private static final Set<String> POLICIES_TO_CHECK = Set.of("POLICY_A", "POLICY_B");

  private static final Bundle bundle1 = generateBundle("12345");
  private static final Bundle bundle2 = generateBundle("67890");

  private static Bundle generateBundle(String id) {
    var patient = new Patient();
    var identifier =
        new Identifier()
            .setSystem("https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym")
            .setValue(id);
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
    var outerBundle = new Bundle();
    outerBundle.addEntry().setResource(bundle1);
    outerBundle.addEntry().setResource(bundle2);

    var consentedPatients =
        ConsentedPatientExtractor.extractConsentedPatients(
            PATIENT_IDENTIFIER_SYSTEM, POLICY_SYSTEM, outerBundle, POLICIES_TO_CHECK);

    var result = consentedPatients.collect(Collectors.toList());
    assertThat(result).hasSize(2);
    assertThat(result.get(0).id()).isEqualTo("12345");
    assertThat(result.get(0).patientIdentifierSystem()).isEqualTo(PATIENT_IDENTIFIER_SYSTEM);
    assertThat(result.get(1).id()).isEqualTo("67890");
    assertThat(result.get(1).patientIdentifierSystem()).isEqualTo(PATIENT_IDENTIFIER_SYSTEM);
  }

  @Test
  void extractConsentedPatient() {
    var consentedPatient =
        ConsentedPatientExtractor.extractConsentedPatient(
            PATIENT_IDENTIFIER_SYSTEM, POLICY_SYSTEM, bundle1, POLICIES_TO_CHECK);

    assertThat(consentedPatient).isPresent();
    assertThat(consentedPatient.get().id()).isEqualTo("12345");
    assertThat(consentedPatient.get().patientIdentifierSystem())
        .isEqualTo(PATIENT_IDENTIFIER_SYSTEM);
  }

  @Test
  void extractConsentedPatientWithUnknownPoliciesYieldsEmptyResult() {
    var consentedPatient =
        ConsentedPatientExtractor.extractConsentedPatient(
            PATIENT_IDENTIFIER_SYSTEM, POLICY_SYSTEM, bundle1, Set.of("Unknown Policy"));

    assertThat(consentedPatient).isEmpty();
  }

  @Test
  void hasAllPolicies() {
    var result =
        ConsentedPatientExtractor.hasAllPolicies(POLICY_SYSTEM, bundle1, POLICIES_TO_CHECK);

    assertThat(result).isTrue();
  }
}
