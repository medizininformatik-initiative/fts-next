package care.smith.fts.util;

import static care.smith.fts.util.fhir.FhirUtils.typedResourceStream;
import static java.util.Objects.requireNonNull;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatient.ConsentedPolicies;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;

/**
 * Extracts consented patients from bundles, each holding a patient and its consents.
 *
 * @param patientIdentifierSystem the system used for patient identifiers in the result
 * @param requiredPolicies the policies a patient has to consent to
 * @param patientIdentifierExtractor function to extract the patient identifier from a bundle
 */
public record ConsentedPatientExtractor(
    String patientIdentifierSystem,
    RequiredPolicies requiredPolicies,
    Function<Bundle, Optional<String>> patientIdentifierExtractor) {

  public ConsentedPatientExtractor {
    requireNonNull(patientIdentifierSystem, "Patient identifier system cannot be null");
    requireNonNull(requiredPolicies, "Required policies cannot be null");
    requireNonNull(patientIdentifierExtractor, "Patient identifier extractor cannot be null");
  }

  /** The consented patients of the given bundles. */
  public Stream<ConsentedPatient> consentedPatients(Stream<Bundle> bundles) {
    return bundles.map(this::consentedPatient).flatMap(Optional::stream);
  }

  /**
   * The consented patient of the given bundle.
   *
   * @return the patient, if it has an identifier and all required policies are consented to
   */
  public Optional<ConsentedPatient> consentedPatient(Bundle bundle) {
    return patientIdentifierExtractor
        .apply(bundle)
        .map(id -> new ConsentedPatient(id, patientIdentifierSystem, consentedPolicies(bundle)))
        .filter(p -> p.consentedPolicies().hasAllPolicies(requiredPolicies.codes()));
  }

  private ConsentedPolicies consentedPolicies(Bundle bundle) {
    return requiredPolicies.consentedPolicies(bundle);
  }

  /**
   * The value of the first identifier of the bundle's patient with the given system.
   *
   * @param patientIdentifierSystem the system used for patient identifiers
   * @param bundle the bundle containing the patient resource
   * @return the patient identifier, if found
   */
  public static Optional<String> getPatientIdentifier(
      String patientIdentifierSystem, Bundle bundle) {
    return typedResourceStream(bundle, Patient.class)
        .flatMap(p -> p.getIdentifier().stream())
        .filter(id -> id.getSystem().equals(patientIdentifierSystem))
        .map(Identifier::getValue)
        .findFirst();
  }
}
