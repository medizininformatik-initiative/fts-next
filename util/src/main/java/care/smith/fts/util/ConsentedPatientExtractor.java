package care.smith.fts.util;

import static care.smith.fts.util.fhir.FhirUtils.typedResourceStream;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatient.ConsentedPolicies;
import care.smith.fts.api.Period;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Consent.ConsentProvisionType;
import org.hl7.fhir.r4.model.Consent.ConsentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Interface for extracting consented patients from FHIR bundles. This interface contains all the
 * shared logic for processing consent information as static methods, while allowing implementations
 * to define how patient identifiers are extracted from bundles.
 *
 * <p>The extraction process:
 *
 * <ul>
 *   <li>Searches for Patient resources within bundles to extract patient identifiers
 *   <li>Examines Consent resources to determine which policies have been consented to
 *   <li>Validates that patients have consented to all required policies
 *   <li>Extracts consent periods from provision components
 *   <li>Returns only patients who have provided consent for all specified policies
 * </ul>
 *
 * @see ConsentedPatient
 * @see ConsentedPolicies
 * @see Period
 */
public interface ConsentedPatientExtractor {

  /**
   * Retrieves a stream of consented patients from the given stream of bundles.
   *
   * @param patientIdentifierSystem the system used for patient identifiers in the result
   * @param policySystem the system used for policy codes
   * @param bundles the stream of bundles to process
   * @param policiesToCheck the set of policies to check for consent
   * @param patientIdentifierExtractor function to extract patient identifier from a bundle
   * @return a stream of consented patients
   */
  static Stream<ConsentedPatient> processConsentedPatients(
      String patientIdentifierSystem,
      String policySystem,
      Stream<Bundle> bundles,
      Set<String> policiesToCheck,
      Function<Bundle, Optional<String>> patientIdentifierExtractor) {
    return bundles
        .map(
            b ->
                processConsentedPatient(
                    patientIdentifierSystem,
                    policySystem,
                    b,
                    policiesToCheck,
                    patientIdentifierExtractor))
        .filter(Optional::isPresent)
        .map(Optional::get);
  }

  /**
   * Extracts the consented patient from the given bundle.
   *
   * @param patientIdentifierSystem the system used for patient identifiers in the result
   * @param policySystem the system used for policy codes
   * @param bundle the bundle from which the consented patient is extracted
   * @param policiesToCheck the policies the patient has to consent to
   * @param patientIdentifierExtractor function to extract patient identifier from a bundle
   * @return an {@link Optional} containing a {@link ConsentedPatient}, if all policiesToCheck are
   *     consented to
   */
  static Optional<ConsentedPatient> processConsentedPatient(
      String patientIdentifierSystem,
      String policySystem,
      Bundle bundle,
      Set<String> policiesToCheck,
      Function<Bundle, Optional<String>> patientIdentifierExtractor) {
    return patientIdentifierExtractor
        .apply(bundle)
        .flatMap(
            patientIdentifier -> {
              var consentedPolicies = getConsentedPolicies(policySystem, bundle, policiesToCheck);
              if (consentedPolicies.hasAllPolicies(policiesToCheck)) {
                return Optional.of(
                    new ConsentedPatient(
                        patientIdentifier, patientIdentifierSystem, consentedPolicies));
              } else {
                return Optional.empty();
              }
            });
  }

  /**
   * Checks if the bundle has consented to all given policies in policiesToCheck.
   *
   * @param policySystem the system used for policy codes
   * @param bundle the bundle to check
   * @param policiesToCheck the set of policies to check for consent
   * @return true if all policies are consented to, false otherwise
   */
  static boolean hasAllPolicies(String policySystem, Bundle bundle, Set<String> policiesToCheck) {
    var consentedPolicies = getConsentedPolicies(policySystem, bundle, policiesToCheck);
    return consentedPolicies.hasAllPolicies(policiesToCheck);
  }

  /**
   * Retrieves the consented policies from the given bundle.
   *
   * @param policySystem the system used for policy codes
   * @param bundle the bundle containing the consent resources
   * @param policiesToCheck the set of policies to check for consent
   * @return the consented policies
   */
  static ConsentedPolicies getConsentedPolicies(
      String policySystem, Bundle bundle, Set<String> policiesToCheck) {
    var fromProvisions =
        getPermitProvisionsStream(bundle)
            .map(p -> getConsentedPoliciesFromProvision(policySystem, p, policiesToCheck))
            .toList();
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
   * Retrieves the nested permit provisions of all active consents in the given bundle.
   *
   * @param bundle the bundle containing the consent resources
   * @return a stream of permit provision components
   */
  static Stream<Consent.ProvisionComponent> getPermitProvisionsStream(Bundle bundle) {
    return typedResourceStream(bundle, Consent.class)
        .filter(ConsentedPatientExtractor::isActive)
        .flatMap(c -> c.getProvision().getProvision().stream())
        .filter(ConsentedPatientExtractor::isPermit);
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

  private static void warnAboutSkippedProvisions(long skipped) {
    if (skipped > 0) {
      log()
          .warn(
              "Skipping {} permit provisions with missing period start or unreadable period",
              skipped);
    }
  }

  private static Logger log() {
    return LoggerFactory.getLogger(ConsentedPatientExtractor.class);
  }

  private static boolean isActive(Consent consent) {
    return consent.getStatus() == ConsentState.ACTIVE;
  }

  private static boolean isPermit(Consent.ProvisionComponent provision) {
    return provision.getType() == ConsentProvisionType.PERMIT;
  }

  /**
   * Retrieves the consented policies from the given provision component.
   *
   * @param policySystem the system used for policy codes
   * @param provision the provision component to process
   * @param policiesToCheck the set of policies to check for consent
   * @return the consented policies, or empty if the provision period cannot be evaluated
   */
  static Optional<ConsentedPolicies> getConsentedPoliciesFromProvision(
      String policySystem, Consent.ProvisionComponent provision, Set<String> policiesToCheck) {
    return readPeriod(provision)
        .map(period -> consentedPolicies(policySystem, provision, policiesToCheck, period));
  }

  private static ConsentedPolicies consentedPolicies(
      String policySystem,
      Consent.ProvisionComponent provision,
      Set<String> policiesToCheck,
      Period period) {
    var consentedPolicies = new ConsentedPolicies();
    provision.getCode().stream()
        .flatMap(c -> extractPolicyFromCodeableConcept(policySystem, policiesToCheck, c))
        .distinct()
        .forEach(p -> consentedPolicies.put(p, period));
    return consentedPolicies;
  }

  /**
   * Reads the period of a provision. A provision without start value or with an unreadable start or
   * end cannot be evaluated, so it grants no consent.
   */
  private static Optional<Period> readPeriod(Consent.ProvisionComponent provision) {
    return Optional.of(provision)
        .filter(ConsentedPatientExtractor::hasStartValue)
        .flatMap(p -> toPeriod(p.getPeriod()));
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

  /**
   * Extracts policies from the given codeable concept.
   *
   * @param policySystem the system used for policy codes
   * @param policiesToCheck the set of policies to check for consent
   * @param c the codeable concept containing the policy codes
   * @return a stream of policy codes that match the policiesToCheck
   */
  static Stream<String> extractPolicyFromCodeableConcept(
      String policySystem, Set<String> policiesToCheck, CodeableConcept c) {
    return c.getCoding().stream()
        .filter(coding -> coding.getSystem().equals(policySystem))
        .map(Coding::getCode)
        .filter(policiesToCheck::contains);
  }

  /**
   * Retrieves the patient identifier from the given bundle using the specified patient identifier
   * system.
   *
   * @param patientIdentifierSystem the system used for patient identifiers
   * @param bundle the bundle containing the patient resource
   * @return an {@link Optional} containing the patient identifier, if found
   */
  static Optional<String> getPatientIdentifier(String patientIdentifierSystem, Bundle bundle) {
    return typedResourceStream(bundle, Patient.class)
        .flatMap(p -> p.getIdentifier().stream())
        .filter(id -> id.getSystem().equals(patientIdentifierSystem))
        .map(Identifier::getValue)
        .findFirst();
  }
}
