package care.smith.fts.util;

import static care.smith.fts.util.fhir.FhirUtils.typedResourceStream;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatient.ConsentedPolicies;
import care.smith.fts.api.Period;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.*;

/**
 * Interface for extracting consented patients from FHIR bundles returned by FHIR servers. Unlike
 * gICS, FHIR servers typically use the actual patient identifier systems in their responses. This
 * interface uses the provided patient identifier system both for extraction and for creating
 * ConsentedPatient objects.
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
public interface FhirConsentedPatientExtractor {

  /**
   * Retrieves a stream of consented patients from the given stream of bundles.
   *
   * @param patientIdentifierSystem the system used for patient identifiers
   * @param policySystem the system used for policy codes
   * @param bundles the stream of bundles to process
   * @param policiesToCheck the set of policies to check for consent
   * @return a stream of consented patients
   */
  static Stream<ConsentedPatient> getConsentedPatients(
      String patientIdentifierSystem,
      String policySystem,
      Stream<Bundle> bundles,
      Set<String> policiesToCheck) {
    return ConsentedPatientExtractorBase.processConsentedPatients(
        patientIdentifierSystem,
        policySystem,
        bundles,
        policiesToCheck,
        bundle -> getPatientIdentifier(patientIdentifierSystem, bundle));
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
