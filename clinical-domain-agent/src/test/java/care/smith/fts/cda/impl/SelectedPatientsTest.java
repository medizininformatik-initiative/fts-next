package care.smith.fts.cda.impl;

import static care.smith.fts.test.LogRecorder.messages;
import static care.smith.fts.test.LogRecorder.recordLog;
import static care.smith.fts.util.fhir.FhirUtils.toBundle;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.CohortEntry.Consented;
import care.smith.fts.api.CohortEntry.Excluded;
import care.smith.fts.api.CohortEntry.NotConsented;
import care.smith.fts.util.ConsentedPatientExtractor;
import care.smith.fts.util.RequiredPolicies;
import ch.qos.logback.classic.Level;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SelectedPatientsTest {

  private static final String PID_SYSTEM = "http://example.org/pid";
  private static final String POLICY_SYSTEM = "http://example.org/policy";
  private static final ConsentedPatientExtractor EXTRACTOR =
      new ConsentedPatientExtractor(
          PID_SYSTEM, new RequiredPolicies(POLICY_SYSTEM, Set.of("A", "B")), PID_SYSTEM);
  private static final String NO_START_A_B =
      "Consent for policy A has no start date; Consent for policy B has no start date";

  private SelectedPatients selected;

  @BeforeEach
  void setUp() {
    selected = new SelectedPatients(EXTRACTOR);
  }

  @Test
  void consentedPatientIsSelected() {
    var entries = selected.select(List.of(inner("x", permit("A"), permit("B"))));

    assertThat(entries).singleElement().isInstanceOf(Consented.class);
  }

  @Test
  void patientsNotConsentedOrExcludedAreReturnedAtOnce() {
    var entries =
        selected.select(
            List.of(inner("x", permit("A")), inner("y", withoutStart("A"), withoutStart("B"))));

    assertThat(entries).containsExactly(new NotConsented("x"), new Excluded("y", NO_START_A_B));
  }

  @Test
  void patientsWithSameIdentifierOnOnePageAreClassifiedTogether() {
    var entries = selected.select(List.of(inner("x", permit("A")), inner("x", permit("B"))));

    assertThat(entries).singleElement().isInstanceOf(Consented.class);
  }

  @Test
  void patientsWithoutConsentAreDropped() {
    var withoutConsent = Stream.of(inner("x").getEntryFirstRep().getResource()).collect(toBundle());

    assertThat(selected.select(List.of(withoutConsent))).isEmpty();
  }

  @Test
  void patientWithoutConsentIsMergedWithPatientResourceWithConsent() {
    var withoutConsent = Stream.of(inner("x").getEntryFirstRep().getResource()).collect(toBundle());

    var entries = selected.select(List.of(withoutConsent, inner("x", permit("A"), permit("B"))));

    assertThat(entries).singleElement().isInstanceOf(Consented.class);
  }

  @Test
  void patientsWithoutIdentifierAndConsentAreCountedInWarning() {
    var unidentified = Stream.<Resource>of(new Patient()).collect(toBundle());

    var events =
        recordLog(
            ConsentedPatientExtractor.class,
            Level.WARN,
            () -> assertThat(selected.select(List.of(unidentified))).isEmpty());

    assertThat(messages(events)).singleElement().asString().contains("Ignoring 1 patients");
  }

  @Test
  void patientsWithoutIdentifierAreDropped() {
    var unidentified = inner("x", permit("A"), permit("B"));
    unidentified.getEntryFirstRep().setResource(new Patient());

    assertThat(selected.select(List.of(unidentified))).isEmpty();
  }

  @Test
  void patientSelectedOnEarlierPageIsDroppedFromLaterPages() {
    selected.select(List.of(inner("x", permit("A"), permit("B"))));

    var entries =
        selected.select(
            List.of(
                inner("x", permit("A"), permit("B")),
                inner("y", permit("A"), permit("B")),
                inner("y", permit("A"))));

    assertThat(entries).singleElement().isInstanceOf(Consented.class);
    assertThat(selected.select(List.of(inner("x", withoutStart("A"))))).isEmpty();
    assertThat(selected.select(List.of(inner("x", permit("A"))))).isEmpty();
  }

  @Test
  void patientSelectedOnEarlierPageIsNotClassifiedAgain() {
    selected.select(List.of(inner("x", permit("A"), permit("B"))));

    var events =
        recordLog(
            RequiredPolicies.class,
            Level.WARN,
            () -> selected.select(List.of(inner("x", withoutStart("A")))));

    assertThat(events).isEmpty();
  }

  @Test
  void patientNotSelectedOnEarlierPageCanBeSelectedOnLaterPage() {
    selected.select(List.of(inner("x", permit("A"))));

    var entries = selected.select(List.of(inner("x", permit("A"), permit("B"))));

    assertThat(entries).singleElement().isInstanceOf(Consented.class);
  }

  @Test
  void warnsWithCountOfPatientsAlreadySelected() {
    selected.select(
        List.of(inner("x", permit("A"), permit("B")), inner("y", permit("A"), permit("B"))));
    selected.select(List.of(inner("x", permit("A"))));
    selected.select(List.of(inner("y", withoutStart("A"))));

    var events = recordLog(SelectedPatients.class, Level.WARN, selected::warnAboutIgnored);

    assertThat(messages(events))
        .singleElement()
        .isEqualTo("Ignoring 2 patients already selected on an earlier page");
  }

  @Test
  void doesNotWarnWithoutPatientsAlreadySelected() {
    selected.select(List.of(inner("x", permit("A"), permit("B"))));

    var events = recordLog(SelectedPatients.class, Level.WARN, selected::warnAboutIgnored);

    assertThat(events).isEmpty();
  }

  private static Bundle inner(String identifier, Consent.ProvisionComponent... provisions) {
    var patient = new Patient();
    patient.addIdentifier(new Identifier().setSystem(PID_SYSTEM).setValue(identifier));
    var consent =
        new Consent()
            .setStatus(Consent.ConsentState.ACTIVE)
            .setProvision(new Consent.ProvisionComponent().setProvision(List.of(provisions)));
    return Stream.of(patient, consent).collect(toBundle());
  }

  private static Consent.ProvisionComponent permit(String policy) {
    return withoutStart(policy).setPeriod(new Period().setStart(new Date(0)));
  }

  private static Consent.ProvisionComponent withoutStart(String policy) {
    return new Consent.ProvisionComponent()
        .setType(Consent.ConsentProvisionType.PERMIT)
        .addCode(
            new CodeableConcept().addCoding(new Coding().setSystem(POLICY_SYSTEM).setCode(policy)));
  }
}
