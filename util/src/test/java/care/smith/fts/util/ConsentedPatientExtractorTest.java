package care.smith.fts.util;

import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Consent.ConsentState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;

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
                bundle -> getPatientIdentifier(bundle))
            .collect(Collectors.toList());

    assertThat(result).hasSize(2); // Only bundles with all policies should be included
    assertThat(result.get(0).identifier()).isEqualTo("12345");
    assertThat(result.get(0).patientIdentifierSystem()).isEqualTo(PATIENT_IDENTIFIER_SYSTEM);
    assertThat(result.get(1).identifier()).isEqualTo("67890");
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
            b -> getPatientIdentifier(b));

    assertThat(result).isPresent();
    assertThat(result.get().identifier()).isEqualTo("12345");
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
            b -> getPatientIdentifier(b));

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
  void deniedProvisionsDoNotGrantConsent() {
    var bundle =
        bundleWithProvisions(
            "12345", deniedProvisionComponent("POLICY_A"), deniedProvisionComponent("POLICY_B"));

    var result = ConsentedPatientExtractor.hasAllPolicies(POLICY_SYSTEM, bundle, POLICIES_TO_CHECK);

    assertThat(result).isFalse();
  }

  @Test
  void deniedProvisionDoesNotCompletePermittedPolicies() {
    var bundle =
        bundleWithProvisions(
            "12345", permittedProvisionComponent("POLICY_A"), deniedProvisionComponent("POLICY_B"));

    var result =
        ConsentedPatientExtractor.processConsentedPatient(
            PATIENT_IDENTIFIER_SYSTEM,
            POLICY_SYSTEM,
            bundle,
            POLICIES_TO_CHECK,
            ConsentedPatientExtractorTest::getPatientIdentifier);

    assertThat(result).isEmpty();
  }

  @ParameterizedTest
  @EnumSource(
      value = ConsentState.class,
      names = {"ACTIVE"},
      mode = EnumSource.Mode.EXCLUDE)
  void nonActiveConsentsDoNotGrantConsent(ConsentState status) {
    var bundle =
        bundleWithProvisions(
            "12345",
            status,
            permittedProvisionComponent("POLICY_A"),
            permittedProvisionComponent("POLICY_B"));

    var result = ConsentedPatientExtractor.hasAllPolicies(POLICY_SYSTEM, bundle, POLICIES_TO_CHECK);

    assertThat(result).isFalse();
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
  void getConsentedPoliciesFromProvision_withDateOnlyPeriod() {
    var provision =
        new Consent.ProvisionComponent()
            .setType(Consent.ConsentProvisionType.PERMIT)
            .setCode(
                List.of(
                    new CodeableConcept()
                        .addCoding(new Coding().setSystem(POLICY_SYSTEM).setCode("POLICY_A"))))
            .setPeriod(
                new Period()
                    .setStartElement(new org.hl7.fhir.r4.model.DateTimeType("2024-02-23"))
                    .setEndElement(new org.hl7.fhir.r4.model.DateTimeType("2054-01-31")));

    var result =
        ConsentedPatientExtractor.getConsentedPoliciesFromProvision(
            POLICY_SYSTEM, provision, Set.of("POLICY_A"));

    assertThat(result.hasAllPolicies(Set.of("POLICY_A"))).isTrue();
  }

  @Test
  void getConsentedPoliciesFromProvision_withoutEndIsOpenEnded() {
    var provision =
        new Consent.ProvisionComponent()
            .setType(Consent.ConsentProvisionType.PERMIT)
            .setCode(
                List.of(
                    new CodeableConcept()
                        .addCoding(new Coding().setSystem(POLICY_SYSTEM).setCode("POLICY_A"))))
            .setPeriod(new Period().setStartElement(new DateTimeType("2024-02-23")));

    var result =
        ConsentedPatientExtractor.getConsentedPoliciesFromProvision(
            POLICY_SYSTEM, provision, Set.of("POLICY_A"));

    assertThat(result.getPeriods("POLICY_A"))
        .containsExactly(care.smith.fts.api.Period.parseOpenEnded("2024-02-23"));
  }

  @Test
  void getConsentedPoliciesFromProvision_withEndIsBounded() {
    var provision =
        policyAProvision(
            new Period()
                .setStartElement(new DateTimeType("2024-02-23"))
                .setEndElement(new DateTimeType("2054-01-31")));

    var result =
        ConsentedPatientExtractor.getConsentedPoliciesFromProvision(
            POLICY_SYSTEM, provision, Set.of("POLICY_A"));

    assertThat(result.getPeriods("POLICY_A"))
        .containsExactly(care.smith.fts.api.Period.parse("2024-02-23", "2054-01-31"));
  }

  @Test
  void getConsentedPoliciesFromProvision_withEndWithoutValueIsOpenEnded() {
    var end = new DateTimeType();
    end.addExtension(dataAbsentReason());
    var provision =
        policyAProvision(
            new Period().setStartElement(new DateTimeType("2024-02-23")).setEndElement(end));

    var result =
        ConsentedPatientExtractor.getConsentedPoliciesFromProvision(
            POLICY_SYSTEM, provision, Set.of("POLICY_A"));

    assertThat(result.getPeriods("POLICY_A"))
        .containsExactly(care.smith.fts.api.Period.parseOpenEnded("2024-02-23"));
  }

  @Test
  void getConsentedPoliciesFromProvision_doesNotAddEndToProvision() {
    var period = new Period().setStartElement(new DateTimeType("2024-02-23"));

    ConsentedPatientExtractor.getConsentedPoliciesFromProvision(
        POLICY_SYSTEM, policyAProvision(period), Set.of("POLICY_A"));

    assertThat(period.getNamedProperty("end").hasValues()).isFalse();
  }

  @Test
  void provisionWithoutPeriodIsSkipped() {
    var withoutPeriod = permittedProvisionComponent("POLICY_B").setPeriod(null);
    var bundle =
        bundleWithProvisions("12345", permittedProvisionComponent("POLICY_A"), withoutPeriod);

    var result =
        ConsentedPatientExtractor.getConsentedPolicies(POLICY_SYSTEM, bundle, POLICIES_TO_CHECK);

    assertThat(result.policyNames()).containsExactly("POLICY_A");
  }

  @Test
  void provisionWithStartWithoutValueIsSkipped() {
    var start = new DateTimeType();
    start.addExtension(dataAbsentReason());
    var withoutStartValue = permittedProvisionComponent("POLICY_B");
    withoutStartValue.getPeriod().setStartElement(start);
    var bundle =
        bundleWithProvisions("12345", permittedProvisionComponent("POLICY_A"), withoutStartValue);

    var result =
        ConsentedPatientExtractor.getConsentedPolicies(POLICY_SYSTEM, bundle, POLICIES_TO_CHECK);

    assertThat(result.policyNames()).containsExactly("POLICY_A");
  }

  @Test
  void skippedProvisionsAreCountedInWarning() {
    var withoutPeriod = permittedProvisionComponent("POLICY_B").setPeriod(null);
    var bundle =
        bundleWithProvisions("12345", permittedProvisionComponent("POLICY_A"), withoutPeriod);

    var events =
        recordExtractorLog(
            () ->
                ConsentedPatientExtractor.getConsentedPolicies(
                    POLICY_SYSTEM, bundle, POLICIES_TO_CHECK));

    assertThat(events)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getLevel()).isEqualTo(Level.WARN);
              assertThat(e.getFormattedMessage())
                  .isEqualTo("Skipping 1 permit provisions without period start");
            });
  }

  @Test
  void noWarningWithoutSkippedProvisions() {
    var bundle = generateBundleWithConsent("12345", "POLICY_A", "POLICY_B");

    var events =
        recordExtractorLog(
            () ->
                ConsentedPatientExtractor.getConsentedPolicies(
                    POLICY_SYSTEM, bundle, POLICIES_TO_CHECK));

    assertThat(events).isEmpty();
  }

  @Test
  void provisionWithoutPeriodIsNotAltered() {
    var withoutPeriod = permittedProvisionComponent("POLICY_B").setPeriod(null);
    var bundle = bundleWithProvisions("12345", withoutPeriod);

    ConsentedPatientExtractor.getConsentedPolicies(POLICY_SYSTEM, bundle, POLICIES_TO_CHECK);

    assertThat(withoutPeriod.getNamedProperty("period").hasValues()).isFalse();
  }

  @Test
  void provisionWithoutStartIsNotAltered() {
    var withoutStart = permittedProvisionComponent("POLICY_B");
    withoutStart.getPeriod().setStartElement(null);
    var bundle = bundleWithProvisions("12345", withoutStart);

    ConsentedPatientExtractor.getConsentedPolicies(POLICY_SYSTEM, bundle, POLICIES_TO_CHECK);

    assertThat(withoutStart.getPeriod().getNamedProperty("start").hasValues()).isFalse();
  }

  @Test
  void policyOfAnotherSystemIsNotExtracted() {
    var concept =
        new CodeableConcept()
            .addCoding(new Coding().setSystem("http://other.system").setCode("POLICY_A"));

    var policies =
        ConsentedPatientExtractor.extractPolicyFromCodeableConcept(
                POLICY_SYSTEM, POLICIES_TO_CHECK, concept)
            .collect(toSet());

    assertThat(policies).isEmpty();
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

  private static Bundle generateBundleWithConsent(String patientIdentifier, String... policies) {
    return bundleWithProvisions(
        patientIdentifier,
        Stream.of(policies)
            .map(ConsentedPatientExtractorTest::permittedProvisionComponent)
            .toArray(Consent.ProvisionComponent[]::new));
  }

  private static Bundle bundleWithProvisions(
      String patientIdentifier, Consent.ProvisionComponent... provisions) {
    return bundleWithProvisions(patientIdentifier, ConsentState.ACTIVE, provisions);
  }

  private static Bundle bundleWithProvisions(
      String patientIdentifier, ConsentState status, Consent.ProvisionComponent... provisions) {
    var patient = new Patient();
    patient.addIdentifier(
        new Identifier().setSystem(PATIENT_IDENTIFIER_SYSTEM).setValue(patientIdentifier));

    var consent = new Consent().setStatus(status);
    var mainProvision = new Consent.ProvisionComponent().setType(Consent.ConsentProvisionType.DENY);
    Stream.of(provisions).forEach(mainProvision::addProvision);
    consent.setProvision(mainProvision);

    var bundle = new Bundle();
    bundle.addEntry().setResource(patient);
    bundle.addEntry().setResource(consent);
    return bundle;
  }

  private static List<ILoggingEvent> recordExtractorLog(Runnable action) {
    var logger = (Logger) LoggerFactory.getLogger(ConsentedPatientExtractor.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      action.run();
      return List.copyOf(appender.list);
    } finally {
      logger.detachAppender(appender);
    }
  }

  private static Consent.ProvisionComponent policyAProvision(Period period) {
    return new Consent.ProvisionComponent()
        .setType(Consent.ConsentProvisionType.PERMIT)
        .setCode(
            List.of(
                new CodeableConcept()
                    .addCoding(new Coding().setSystem(POLICY_SYSTEM).setCode("POLICY_A"))))
        .setPeriod(period);
  }

  private static Extension dataAbsentReason() {
    return new Extension(
        "http://hl7.org/fhir/StructureDefinition/data-absent-reason", new CodeType("unknown"));
  }

  private static Consent.ProvisionComponent permittedProvisionComponent(String policy) {
    return provisionComponent(Consent.ConsentProvisionType.PERMIT, policy);
  }

  private static Consent.ProvisionComponent deniedProvisionComponent(String policy) {
    return provisionComponent(Consent.ConsentProvisionType.DENY, policy);
  }

  private static Consent.ProvisionComponent provisionComponent(
      Consent.ConsentProvisionType type, String policy) {
    return new Consent.ProvisionComponent()
        .setType(type)
        .setCode(
            List.of(
                new CodeableConcept()
                    .addCoding(new Coding().setSystem(POLICY_SYSTEM).setCode(policy))))
        .setPeriod(new Period().setStart(new Date(0)).setEnd(new Date(1)));
  }

  private static Optional<String> getPatientIdentifier(Bundle bundle) {
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

  private static Bundle generateBundleWithMultipleIdentifiers(String hospitalId) {
    var patient = new Patient();
    patient.addIdentifier(new Identifier().setSystem(HOSPITAL_PATIENT_SYSTEM).setValue(hospitalId));
    patient.addIdentifier(
        new Identifier().setSystem(ANOTHER_PATIENT_SYSTEM).setValue("OTHER_" + hospitalId));

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
