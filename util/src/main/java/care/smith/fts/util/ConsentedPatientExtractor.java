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
    return getPermitProvisionsStream(bundle)
        .map(
            (provisionComponent) ->
                getConsentedPoliciesFromProvision(
                    policySystem, provisionComponent, policiesToCheck))
        .reduce(
            new ConsentedPolicies(),
            (a, b) -> {
              a.merge(b);
              return a;
            });
  }

  /**
   * Retrieves a stream of provision components with permit provisions from the given bundle.
   *
   * @param bundle the bundle containing the consent resources
   * @return a stream of permit provision components
   */
  static Stream<Consent.ProvisionComponent> getPermitProvisionsStream(Bundle bundle) {
    return typedResourceStream(bundle, Consent.class)
        .flatMap(c -> c.getProvision().getProvision().stream());
  }

  /**
   * Retrieves the consented policies from the given provision component.
   *
   * @param policySystem the system used for policy codes
   * @param ProvisionComponent the provision component to process
   * @param policiesToCheck the set of policies to check for consent
   * @return the consented policies
   */
  static ConsentedPolicies getConsentedPoliciesFromProvision(
      String policySystem,
      Consent.ProvisionComponent ProvisionComponent,
      Set<String> policiesToCheck) {
    String start = ProvisionComponent.getPeriod().getStartElement().asStringValue();
    String end = ProvisionComponent.getPeriod().getEndElement().asStringValue();

    var code = ProvisionComponent.getCode();
    var consentedPolicies = new ConsentedPolicies();
    code.stream()
        .flatMap(c -> extractPolicyFromCodeableConcept(policySystem, policiesToCheck, c))
        .distinct()
        .forEach(p -> consentedPolicies.put(p, Period.parse(start, end)));
    return consentedPolicies;
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
