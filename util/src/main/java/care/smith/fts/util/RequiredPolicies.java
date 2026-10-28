package care.smith.fts.util;

import static care.smith.fts.util.fhir.FhirUtils.typedResourceStream;
import static java.util.Objects.requireNonNull;

import care.smith.fts.api.ConsentedPatient.ConsentedPolicies;
import care.smith.fts.api.Period;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Consent.ConsentProvisionType;
import org.hl7.fhir.r4.model.Consent.ConsentState;

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
  }

  /** Whether the consents in the bundle cover all required policies. */
  public boolean allConsented(Bundle bundle) {
    return consentedPolicies(bundle).hasAllPolicies(codes);
  }

  /** The required policies the consents in the bundle cover, with their periods. */
  public ConsentedPolicies consentedPolicies(Bundle bundle) {
    var fromProvisions = permitProvisions(bundle).map(this::consentedPolicies).toList();
    warnAboutSkippedProvisions(fromProvisions.stream().filter(Optional::isEmpty).count());
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
   * The reason of the first faulty provision in the bundle covering one of the missing policies.
   * Contains no dates.
   *
   * @param missingPolicies the required policies the bundle's consents do not cover
   */
  public Optional<String> faultReason(Bundle bundle, Set<String> missingPolicies) {
    return permitProvisions(bundle).flatMap(p -> faultReasons(p, missingPolicies)).findFirst();
  }

  /** One reason per missing policy of the provision, if the provision is faulty. */
  private Stream<String> faultReasons(
      Consent.ProvisionComponent provision, Set<String> missingPolicies) {
    var policies = missingCodes(provision, missingPolicies);
    return provisionFault(provision).stream().flatMap(fault -> policies.map(fault::formatted));
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
        .flatMap(c -> c.getProvision().getProvision().stream())
        .filter(RequiredPolicies::isPermit);
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
    return concept.getCoding().stream()
        .filter(coding -> coding.getSystem().equals(policySystem))
        .map(Coding::getCode)
        .filter(codes::contains);
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

  private static void warnAboutSkippedProvisions(long skipped) {
    if (skipped > 0) {
      log.warn(
          "Skipping {} permit provisions with missing period start or unreadable period", skipped);
    }
  }

  private static boolean isActive(Consent consent) {
    return consent.getStatus() == ConsentState.ACTIVE;
  }

  private static boolean isPermit(Consent.ProvisionComponent provision) {
    return provision.getType() == ConsentProvisionType.PERMIT;
  }
}
