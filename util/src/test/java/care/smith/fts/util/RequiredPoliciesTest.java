package care.smith.fts.util;

import static care.smith.fts.util.ConsentBundles.POLICY_SYSTEM;
import static care.smith.fts.util.ConsentBundles.bundleWithProvisions;
import static care.smith.fts.util.ConsentBundles.dataAbsentReason;
import static care.smith.fts.util.ConsentBundles.deniedProvisionComponent;
import static care.smith.fts.util.ConsentBundles.generateBundleWithConsent;
import static care.smith.fts.util.ConsentBundles.parsedProvision;
import static care.smith.fts.util.ConsentBundles.permittedProvisionComponent;
import static care.smith.fts.util.ConsentBundles.policyAProvision;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Consent.ConsentState;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Period;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

class RequiredPoliciesTest {

  private static final RequiredPolicies POLICIES_A_B =
      new RequiredPolicies(POLICY_SYSTEM, Set.of("POLICY_A", "POLICY_B"));
  private static final RequiredPolicies POLICY_A =
      new RequiredPolicies(POLICY_SYSTEM, Set.of("POLICY_A"));

  @Test
  void requiresPolicySystem() {
    assertThatNullPointerException()
        .isThrownBy(() -> new RequiredPolicies(null, Set.of("POLICY_A")))
        .withMessageContaining("policy system");
  }

  @Test
  void requiresPolicies() {
    assertThatNullPointerException()
        .isThrownBy(() -> new RequiredPolicies(POLICY_SYSTEM, null))
        .withMessageContaining("policies");
  }

  @Test
  void deniedProvisionsDoNotGrantConsent() {
    var bundle =
        bundleWithProvisions(
            "12345", deniedProvisionComponent("POLICY_A"), deniedProvisionComponent("POLICY_B"));

    assertThat(POLICIES_A_B.allConsented(bundle)).isFalse();
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

    assertThat(POLICIES_A_B.allConsented(bundle)).isFalse();
  }

  @Test
  void allConsented_withAllRequired() {
    var bundle = generateBundleWithConsent("12345", "POLICY_A", "POLICY_B");

    assertThat(POLICIES_A_B.allConsented(bundle)).isTrue();
  }

  @Test
  void allConsented_withMissingPolicy() {
    var bundle = generateBundleWithConsent("12345", "POLICY_A");

    assertThat(POLICIES_A_B.allConsented(bundle)).isFalse();
  }

  @Test
  void allConsented_withExtraPolicies() {
    var bundle = generateBundleWithConsent("12345", "POLICY_A", "POLICY_B", "POLICY_C");

    assertThat(POLICIES_A_B.allConsented(bundle)).isTrue();
  }

  @Test
  void consentedPolicies() {
    var bundle = generateBundleWithConsent("12345", "POLICY_A", "POLICY_B", "POLICY_C");

    var result = POLICIES_A_B.consentedPolicies(bundle);

    assertThat(result.hasAllPolicies(Set.of("POLICY_A", "POLICY_B"))).isTrue();
    assertThat(result.hasAllPolicies(Set.of("POLICY_A", "POLICY_B", "POLICY_C")))
        .isFalse(); // POLICY_C is not required
  }

  @Test
  void permitProvisions() {
    var bundle = generateBundleWithConsent("12345", "POLICY_A", "POLICY_B");

    var provisions = RequiredPolicies.permitProvisions(bundle).toList();

    assertThat(provisions).hasSize(2);
  }

  @Test
  void consentedPoliciesOfProvision() {
    var provision = policyAProvision(new Period().setStart(new Date(0)).setEnd(new Date(1)));

    var result = POLICIES_A_B.consentedPolicies(provision).orElseThrow();

    assertThat(result.hasAllPolicies(Set.of("POLICY_A"))).isTrue();
    assertThat(result.hasAllPolicies(Set.of("POLICY_A", "POLICY_B"))).isFalse();
  }

  @Test
  void consentedPoliciesOfProvision_withDateOnlyPeriod() {
    var provision =
        policyAProvision(
            new Period()
                .setStartElement(new DateTimeType("2024-02-23"))
                .setEndElement(new DateTimeType("2054-01-31")));

    var result = POLICY_A.consentedPolicies(provision).orElseThrow();

    assertThat(result.hasAllPolicies(Set.of("POLICY_A"))).isTrue();
  }

  @Test
  void consentedPoliciesOfProvision_withoutEndIsOpenEnded() {
    var provision = policyAProvision(new Period().setStartElement(new DateTimeType("2024-02-23")));

    var result = POLICY_A.consentedPolicies(provision).orElseThrow();

    assertThat(result.getPeriods("POLICY_A"))
        .containsExactly(care.smith.fts.api.Period.parseOpenEnded("2024-02-23"));
  }

  @Test
  void consentedPoliciesOfProvision_withEndIsBounded() {
    var provision =
        policyAProvision(
            new Period()
                .setStartElement(new DateTimeType("2024-02-23"))
                .setEndElement(new DateTimeType("2054-01-31")));

    var result = POLICY_A.consentedPolicies(provision).orElseThrow();

    assertThat(result.getPeriods("POLICY_A"))
        .containsExactly(care.smith.fts.api.Period.parse("2024-02-23", "2054-01-31"));
  }

  @Test
  void consentedPoliciesOfProvision_withEndWithoutValueIsOpenEnded() {
    var end = new DateTimeType();
    end.addExtension(dataAbsentReason());
    var provision =
        policyAProvision(
            new Period().setStartElement(new DateTimeType("2024-02-23")).setEndElement(end));

    var result = POLICY_A.consentedPolicies(provision).orElseThrow();

    assertThat(result.getPeriods("POLICY_A"))
        .containsExactly(care.smith.fts.api.Period.parseOpenEnded("2024-02-23"));
  }

  @Test
  void consentedPoliciesOfProvision_doesNotAddEndToProvision() {
    var period = new Period().setStartElement(new DateTimeType("2024-02-23"));

    POLICY_A.consentedPolicies(policyAProvision(period));

    assertThat(period.getNamedProperty("end").hasValues()).isFalse();
  }

  @Test
  void provisionWithoutPeriodIsSkipped() {
    var withoutPeriod = permittedProvisionComponent("POLICY_B").setPeriod(null);
    var bundle =
        bundleWithProvisions("12345", permittedProvisionComponent("POLICY_A"), withoutPeriod);

    var result = POLICIES_A_B.consentedPolicies(bundle);

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

    var result = POLICIES_A_B.consentedPolicies(bundle);

    assertThat(result.policyNames()).containsExactly("POLICY_A");
  }

  @ParameterizedTest
  @ValueSource(strings = {"2024-02-23T10:00:00", " 2024-02-23"})
  void provisionWithUnreadableStartIsSkipped(String start) {
    var unreadable = parsedProvision("POLICY_B", start, "2054-01-31");
    var bundle = bundleWithProvisions("12345", permittedProvisionComponent("POLICY_A"), unreadable);

    var result = POLICIES_A_B.consentedPolicies(bundle);

    assertThat(result.policyNames()).containsExactly("POLICY_A");
  }

  @ParameterizedTest
  @ValueSource(strings = {"2054-01-31T10:00:00", " 2054-01-31"})
  void provisionWithUnreadableEndIsSkipped(String end) {
    var unreadable = parsedProvision("POLICY_B", "2024-02-23", end);
    var bundle = bundleWithProvisions("12345", permittedProvisionComponent("POLICY_A"), unreadable);

    var result = POLICIES_A_B.consentedPolicies(bundle);

    assertThat(result.policyNames()).containsExactly("POLICY_A");
  }

  @Test
  void unreadableAndStartlessProvisionsAreCountedInOneWarningWithoutDates() {
    var withoutPeriod = permittedProvisionComponent("POLICY_A").setPeriod(null);
    var unreadable = parsedProvision("POLICY_B", "2024-02-23T10:00:00", "2054-01-31");
    var bundle = bundleWithProvisions("12345", withoutPeriod, unreadable);

    var events = recordLog(() -> POLICIES_A_B.consentedPolicies(bundle));

    assertThat(events)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getLevel()).isEqualTo(Level.WARN);
              assertThat(e.getFormattedMessage())
                  .isEqualTo(
                      "Skipping 2 permit provisions with missing period start or unreadable"
                          + " period");
            });
  }

  @Test
  void skippedProvisionsAreCountedInWarning() {
    var withoutPeriod = permittedProvisionComponent("POLICY_B").setPeriod(null);
    var bundle =
        bundleWithProvisions("12345", permittedProvisionComponent("POLICY_A"), withoutPeriod);

    var events = recordLog(() -> POLICIES_A_B.consentedPolicies(bundle));

    assertThat(events)
        .singleElement()
        .satisfies(
            e -> {
              assertThat(e.getLevel()).isEqualTo(Level.WARN);
              assertThat(e.getFormattedMessage())
                  .isEqualTo(
                      "Skipping 1 permit provisions with missing period start or unreadable"
                          + " period");
            });
  }

  @Test
  void noWarningWithoutSkippedProvisions() {
    var bundle = generateBundleWithConsent("12345", "POLICY_A", "POLICY_B");

    var events = recordLog(() -> POLICIES_A_B.consentedPolicies(bundle));

    assertThat(events).isEmpty();
  }

  @Test
  void provisionWithoutPeriodIsNotAltered() {
    var withoutPeriod = permittedProvisionComponent("POLICY_B").setPeriod(null);
    var bundle = bundleWithProvisions("12345", withoutPeriod);

    POLICIES_A_B.consentedPolicies(bundle);

    assertThat(withoutPeriod.getNamedProperty("period").hasValues()).isFalse();
  }

  @Test
  void provisionWithoutStartIsNotAltered() {
    var withoutStart = permittedProvisionComponent("POLICY_B");
    withoutStart.getPeriod().setStartElement(null);
    var bundle = bundleWithProvisions("12345", withoutStart);

    POLICIES_A_B.consentedPolicies(bundle);

    assertThat(withoutStart.getPeriod().getNamedProperty("start").hasValues()).isFalse();
  }

  @Test
  void policyOfAnotherSystemIsNotRequired() {
    var concept =
        new CodeableConcept()
            .addCoding(new Coding().setSystem("http://other.system").setCode("POLICY_A"));

    var policies = POLICIES_A_B.requiredCodes(concept).collect(toSet());

    assertThat(policies).isEmpty();
  }

  @Test
  void requiredCodes() {
    var concept =
        new CodeableConcept()
            .addCoding(new Coding().setSystem(POLICY_SYSTEM).setCode("POLICY_A"))
            .addCoding(new Coding().setSystem("http://other.system").setCode("OTHER_POLICY"))
            .addCoding(new Coding().setSystem(POLICY_SYSTEM).setCode("POLICY_B"));
    var required = new RequiredPolicies(POLICY_SYSTEM, Set.of("POLICY_A", "POLICY_B", "POLICY_C"));

    var policies = required.requiredCodes(concept).collect(toSet());

    assertThat(policies).containsExactlyInAnyOrder("POLICY_A", "POLICY_B");
  }

  private static List<ILoggingEvent> recordLog(Runnable action) {
    var logger = (Logger) LoggerFactory.getLogger(RequiredPolicies.class);
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
}
