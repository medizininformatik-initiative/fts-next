package care.smith.fts.cda.impl;

import static care.smith.fts.util.fhir.FhirUtils.resourceStream;
import static care.smith.fts.util.fhir.FhirUtils.toBundle;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.util.ConsentedPatientExtractor;
import care.smith.fts.util.RequiredPolicies;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;

class PatientPageTest {

  private static final String PID_SYSTEM = "http://example.org/pid";
  private static final ConsentedPatientExtractor EXTRACTOR =
      new ConsentedPatientExtractor(
          PID_SYSTEM, new RequiredPolicies("http://example.org/policy", Set.of("A")), PID_SYSTEM);

  @Test
  void identifiersAreTheDistinctIdentifiersOfThePatients() {
    var unidentified = (Patient) new Patient().setId("u");
    var page = page(patient("p1", "x"), patient("p2", "x"), patient("p3", "y"), unidentified);

    assertThat(page.identifiers()).containsExactly("x", "y");
  }

  @Test
  void identifiersPreferTheRequestedOnes() {
    var page =
        PatientPage.of(bundle(patient("p1", "x", "x2")), EXTRACTOR.preferring(List.of("x2")));

    assertThat(page.identifiers()).containsExactly("x2");
  }

  @Test
  void withConsentsAddsConsentsAndOtherPatientsWithTheIdentifier() {
    var duplicate = patient("p2", "x");
    var consents = List.of(bundle(consentOf("p1"), consentOf("p2"), duplicate));

    var combined = page(patient("p1", "x")).withConsents(consents);

    assertThat(ids(combined)).containsExactly("p1", "p2", "c-p1", "c-p2");
  }

  @Test
  void withConsentsAddsPatientsOfThePageAndOfSeveralConsentPagesOnce() {
    var consents =
        List.of(
            bundle(consentOf("p1"), patient("p1", "x"), patient("p2", "x")),
            bundle(consentOf("p2"), patient("p2", "x")));

    var combined = page(patient("p1", "x")).withConsents(consents);

    assertThat(ids(combined)).containsExactly("p1", "p2", "c-p1", "c-p2");
  }

  @Test
  void withConsentsLeavesOutOtherPatientsWithAnotherIdentifierAndTheirConsents() {
    var other = patient("p2", "z", "x");
    var consents = List.of(bundle(consentOf("p1"), consentOf("p2"), other));

    var combined = page(patient("p1", "x")).withConsents(consents);

    assertThat(ids(combined)).containsExactly("p1", "c-p1");
  }

  @Test
  void withConsentsKeepsConsentsOfPatientsNotReturned() {
    var logical = new Consent().setPatient(new Reference().setDisplay("x"));
    logical.setId("logical");
    var consents = List.of(bundle(consentOf("p9"), logical));

    var combined = page(patient("p1", "x")).withConsents(consents);

    assertThat(ids(combined)).containsExactly("p1", "c-p9", "logical");
  }

  @Test
  void withoutConsentPagesHoldsThePatientsOnly() {
    assertThat(ids(page(patient("p1", "x")).withConsents(List.of()))).containsExactly("p1");
  }

  private static PatientPage page(Patient... patients) {
    return PatientPage.of(bundle(patients), EXTRACTOR);
  }

  private static Patient patient(String id, String... identifiers) {
    var patient = new Patient();
    patient.setId(id);
    Stream.of(identifiers)
        .forEach(v -> patient.addIdentifier(new Identifier().setSystem(PID_SYSTEM).setValue(v)));
    return patient;
  }

  private static Consent consentOf(String patientId) {
    var consent = new Consent().setPatient(new Reference("Patient/" + patientId));
    consent.setId("c-" + patientId);
    return consent;
  }

  private static Bundle bundle(Resource... resources) {
    return Stream.of(resources).collect(toBundle());
  }

  private static List<String> ids(Bundle bundle) {
    return resourceStream(bundle).map(r -> r.getIdElement().getIdPart()).toList();
  }
}
