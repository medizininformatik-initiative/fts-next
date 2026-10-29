package care.smith.fts.util;

import static care.smith.fts.util.fhir.FhirUtils.typedResourceStream;
import static com.google.common.collect.Sets.difference;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

import care.smith.fts.api.ConsentedPatient.ConsentedPolicies;
import care.smith.fts.api.Period;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Consent.ConsentProvisionType;
import org.hl7.fhir.r4.model.Consent.ConsentState;
import org.slf4j.event.Level;

/**
 * The policies a patient has to consent to, and how to read them from the consents of a bundle.
 *
 * <p>Only nested permit provisions of active consents grant consent. A provision without start
 * value or with an unreadable period cannot be evaluated, so it grants no consent.
 *
 * @param policySystem the system used for policy codes
 * @param codes the codes of the required policies
 */
@Slf4j
public record RequiredPolicies(String policySystem, Set<String> codes) {

  public RequiredPolicies {
    requireNonNull(policySystem, "Required policy system cannot be null");
    codes = Set.copyOf(requireNonNull(codes, "Required policies cannot be null"));
    if (codes.isEmpty()) {
      throw new IllegalArgumentException("Required policies cannot be empty");
    }
  }

  /** How the consents of a bundle relate to the required policies. */
  public sealed interface Classification {}

  /** The consents cover all required policies, with these periods. */
  public record Covered(ConsentedPolicies policies) implements Classification {}

  /**
   * Each missing policy is covered by a faulty provision only, so the patient has to be reported.
   *
   * @param reason one reason per missing policy, contains no dates
   */
  public record Faulty(String reason) implements Classification {}

  /** The consents do not cover all required policies. */
  public record NotCovered() implements Classification {}

  /** Classifies the consents in the bundle, warning about provisions that cannot be evaluated. */
  public Classification classify(Bundle bundle) {
    return classify(bundle, Level.WARN);
  }

  /**
   * Whether the consents in the bundle cover all required policies, or each missing policy is
   * covered by a faulty provision. Logs skipped provisions at debug level only, as the clinical
   * domain agent warns about them.
   */
  public boolean consentedOrFaulty(Bundle bundle) {
    return !(classify(bundle, Level.DEBUG) instanceof NotCovered);
  }

  private Classification classify(Bundle bundle, Level skippedLevel) {
    var provisions = permitProvisions(bundle).toList();
    var policies = consentedPolicies(provisions, skippedLevel);
    var missingPolicies = difference(codes, policies.policyNames());
    if (missingPolicies.isEmpty()) {
      return new Covered(policies);
    }
    return faultReason(provisions, missingPolicies)
        .<Classification>map(Faulty::new)
        .orElseGet(NotCovered::new);
  }

  private ConsentedPolicies consentedPolicies(
      List<Consent.ProvisionComponent> provisions, Level skippedLevel) {
    var fromProvisions = provisions.stream().map(this::consentedPolicies).toList();
    logSkippedProvisions(fromProvisions.stream().filter(Optional::isEmpty).count(), skippedLevel);
    return fromProvisions.stream()
        .flatMap(Optional::stream)
        .reduce(
            new ConsentedPolicies(),
            (a, b) -> {
              a.merge(b);
              return a;
            });
  }

  /**
   * The reasons of the faulty provisions, if each missing policy is covered by one. Names the first
   * fault of each missing policy, ordered by policy.
   */
  private Optional<String> faultReason(
      List<Consent.ProvisionComponent> provisions, Set<String> missingPolicies) {
    var reasonByPolicy = new TreeMap<String, String>();
    provisions.forEach(p -> faultReasons(p, missingPolicies).forEach(reasonByPolicy::putIfAbsent));
    return Optional.of(reasonByPolicy)
        .filter(reasons -> reasons.keySet().equals(missingPolicies))
        .map(reasons -> String.join("; ", reasons.values()));
  }

  /** The reason per missing policy of the provision, if the provision is faulty. */
  private Map<String, String> faultReasons(
      Consent.ProvisionComponent provision, Set<String> missingPolicies) {
    return provisionFault(provision)
        .map(fault ->
            missingCodes(provision, missingPolicies).collect(toMap(c -> c, fault::formatted)))
        .orElse(Map.of());
  }

  /**
   * The fault of a provision as reason template with a {@code %s} for the policy, or empty if its
   * period is readable.
   */
  private static Optional<String> provisionFault(Consent.ProvisionComponent provision) {
    if (!hasStartValue(provision)) {
      return Optional.of("Consent for policy %s has no start date");
    }
    return toPeriod(provision.getPeriod()).isEmpty()
        ? Optional.of("Consent for policy %s has an unreadable date")
        : Optional.empty();
  }

  /** The distinct codes of the missing policies the provision covers. */
  private Stream<String> missingCodes(
      Consent.ProvisionComponent provision, Set<String> missingPolicies) {
    return provision.getCode().stream()
        .flatMap(this::requiredCodes)
        .filter(missingPolicies::contains)
        .distinct();
  }

  /** The nested permit provisions of all active consents in the bundle. */
  static Stream<Consent.ProvisionComponent> permitProvisions(Bundle bundle) {
    return typedResourceStream(bundle, Consent.class)
        .filter(RequiredPolicies::isActive)
        .flatMap(RequiredPolicies::permitProvisions);
  }

  /** The nested permit provisions of the consent. */
  static Stream<Consent.ProvisionComponent> permitProvisions(Consent consent) {
    return consent.getProvision().getProvision().stream().filter(RequiredPolicies::isPermit);
  }

  /**
   * The required policies the provision covers, with the provision period.
   *
   * @return the consented policies, or empty if the provision period cannot be evaluated
   */
  Optional<ConsentedPolicies> consentedPolicies(Consent.ProvisionComponent provision) {
    return readPeriod(provision).map(period -> consentedPolicies(provision, period));
  }

  private ConsentedPolicies consentedPolicies(Consent.ProvisionComponent provision, Period period) {
    var consentedPolicies = new ConsentedPolicies();
    provision.getCode().stream()
        .flatMap(this::requiredCodes)
        .distinct()
        .forEach(p -> consentedPolicies.put(p, period));
    return consentedPolicies;
  }

  /** The codes of required policies in the codeable concept. */
  Stream<String> requiredCodes(CodeableConcept concept) {
    return concept.getCoding().stream().filter(this::isRequired).map(Coding::getCode);
  }

  /** Whether the coding is one of a required policy. */
  boolean isRequired(Coding coding) {
    return policySystem.equals(coding.getSystem()) && codes.contains(coding.getCode());
  }

  /**
   * Reads the period of a provision. A provision without start value or with an unreadable start or
   * end cannot be evaluated, so it grants no consent.
   */
  private static Optional<Period> readPeriod(Consent.ProvisionComponent provision) {
    return Optional.of(provision)
        .filter(RequiredPolicies::hasStartValue)
        .flatMap(p -> toPeriod(p.getPeriod()));
  }

  /**
   * Checks {@code hasPeriod()} and {@code hasStart()} first, so HAPI does not auto-create empty
   * elements.
   */
  private static boolean hasStartValue(Consent.ProvisionComponent provision) {
    return provision.hasPeriod()
        && provision.getPeriod().hasStart()
        && provision.getPeriod().getStartElement().hasValue();
  }

  /**
   * Converts a FHIR provision period. A provision period without end value is open-ended, as
   * allowed since MII KDS Consent 2026. This includes an end carrying only an extension, e.g.
   * data-absent-reason.
   *
   * @param fhirPeriod the FHIR period of a provision, with start value
   * @return the bounded or open-ended period, or empty if start or end is unreadable
   */
  private static Optional<Period> toPeriod(org.hl7.fhir.r4.model.Period fhirPeriod) {
    var start = fhirPeriod.getStartElement().asStringValue();
    return hasEndValue(fhirPeriod)
        ? Period.tryParse(start, fhirPeriod.getEndElement().asStringValue())
        : Period.tryParseOpenEnded(start);
  }

  /** Checks {@code hasEnd()} first, so HAPI does not auto-create an empty end element. */
  private static boolean hasEndValue(org.hl7.fhir.r4.model.Period fhirPeriod) {
    return fhirPeriod.hasEnd() && fhirPeriod.getEndElement().hasValue();
  }

  private static void logSkippedProvisions(long skipped, Level level) {
    if (skipped > 0) {
      log.atLevel(level)
          .log(
              "Skipping {} permit provisions with missing period start or unreadable period",
              skipped);
    }
  }

  private static boolean isActive(Consent consent) {
    return consent.getStatus() == ConsentState.ACTIVE;
  }

  private static boolean isPermit(Consent.ProvisionComponent provision) {
    return provision.getType() == ConsentProvisionType.PERMIT;
  }
}
