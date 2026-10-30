package care.smith.fts.util;

import static care.smith.fts.util.fhir.FhirUtils.typedResourceStream;
import static com.google.common.collect.Sets.difference;
import static java.util.Objects.requireNonNull;

import care.smith.fts.api.CohortEntry;
import care.smith.fts.api.CohortEntry.Consented;
import care.smith.fts.api.CohortEntry.Excluded;
import care.smith.fts.api.ConsentedPatient;
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

  /** The cohort entries of the given bundles, see {@link #toCohortEntry(Bundle)}. */
  public Stream<CohortEntry> cohortEntries(Stream<Bundle> bundles) {
    return bundles.map(this::toCohortEntry).flatMap(Optional::stream);
  }

  /**
   * Classifies the patient of the given bundle for the cohort.
   *
   * @return {@link Consented} if all required policies are consented to, {@link Excluded} if a
   *     missing policy is only covered by a faulty provision, otherwise empty
   */
  public Optional<CohortEntry> toCohortEntry(Bundle bundle) {
    return patientIdentifierExtractor.apply(bundle).flatMap(id -> classifyPatient(id, bundle));
  }

  /**
   * Consented if no required policy is missing, Excluded if a missing policy is covered by a faulty
   * provision, otherwise empty.
   */
  private Optional<CohortEntry> classifyPatient(String patientIdentifier, Bundle bundle) {
    var consentedPolicies = requiredPolicies.consentedPolicies(bundle);
    var missingPolicies = difference(requiredPolicies.codes(), consentedPolicies.policyNames());
    return Optional.of(
            new ConsentedPatient(patientIdentifier, patientIdentifierSystem, consentedPolicies))
        .filter(p -> missingPolicies.isEmpty())
        .<CohortEntry>map(Consented::new)
        .or(
            () ->
                requiredPolicies
                    .faultReason(bundle, missingPolicies)
                    .map(reason -> new Excluded(patientIdentifier, reason)));
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
