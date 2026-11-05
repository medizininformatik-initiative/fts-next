package care.smith.fts.cda.impl;

import static care.smith.fts.util.ConsentedPatientExtractor.getPatientIdentifier;
import static care.smith.fts.util.fhir.FhirUtils.toBundle;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.CohortEntry.Consented;
import care.smith.fts.api.CohortEntry.Excluded;
import care.smith.fts.api.CohortEntry.NotConsented;
import care.smith.fts.util.ConsentedPatientExtractor;
import care.smith.fts.util.RequiredPolicies;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UnresolvedPatientsTest {

  private static final String PID_SYSTEM = "http://example.org/pid";
  private static final String POLICY_SYSTEM = "http://example.org/policy";
  private static final ConsentedPatientExtractor EXTRACTOR =
      new ConsentedPatientExtractor(
          PID_SYSTEM,
          new RequiredPolicies(POLICY_SYSTEM, Set.of("A", "B")),
          b -> getPatientIdentifier(PID_SYSTEM, b));

  private UnresolvedPatients unresolved;

  @BeforeEach
  void setUp() {
    unresolved = new UnresolvedPatients(EXTRACTOR);
  }

  @Test
  void consentedPatientIsReturnedAtOnce() {
    var entries = unresolved.resolve(List.of(inner("x", permit("A"), permit("B"))));

    assertThat(entries).singleElement().isInstanceOf(Consented.class);
    assertThat(unresolved.remaining()).isEmpty();
  }

  @Test
  void unresolvedPatientIsHeldBackUntilRemaining() {
    var entries = unresolved.resolve(List.of(inner("x", permit("A"))));

    assertThat(entries).isEmpty();
    assertThat(unresolved.remaining()).containsExactly(new NotConsented("x"));
  }

  @Test
  void patientIsConsentedByConsentsOfTwoPages() {
    unresolved.resolve(List.of(inner("x", permit("A"), withoutStart("B"))));

    var entries = unresolved.resolve(List.of(inner("x", permit("B"))));

    assertThat(entries)
        .singleElement()
        .isInstanceOfSatisfying(
            Consented.class,
            c -> assertThat(c.patient().consentedPolicies().policyNames()).containsOnly("A", "B"));
    assertThat(unresolved.remaining()).isEmpty();
  }

  @Test
  void patientStillUnresolvedAfterTwoPagesIsClassifiedByBoth() {
    unresolved.resolve(List.of(inner("x", withoutStart("A"))));
    unresolved.resolve(List.of(inner("x", withoutStart("B"))));

    assertThat(unresolved.remaining())
        .containsExactly(
            new Excluded(
                "x",
                "Consent for policy A has no start date; Consent for policy B has no start date"));
  }

  @Test
  void patientsWithoutIdentifierAreDropped() {
    var unidentified = inner("x", permit("A"), permit("B"));
    unidentified.getEntryFirstRep().setResource(new Patient());

    assertThat(unresolved.resolve(List.of(unidentified))).isEmpty();
    assertThat(unresolved.remaining()).isEmpty();
  }

  @Test
  void remainingKeepsOrderOfArrival() {
    unresolved.resolve(List.of(inner("y", permit("A")), inner("x", permit("A"))));

    assertThat(unresolved.remaining())
        .containsExactly(new NotConsented("y"), new NotConsented("x"));
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
