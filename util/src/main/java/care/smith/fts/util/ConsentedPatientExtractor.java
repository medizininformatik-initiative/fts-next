package care.smith.fts.util;

import static care.smith.fts.util.fhir.FhirUtils.typedResourceStream;
import static java.util.Objects.requireNonNull;

import care.smith.fts.api.CohortEntry;
import care.smith.fts.api.CohortEntry.Consented;
import care.smith.fts.api.CohortEntry.Excluded;
import care.smith.fts.api.CohortEntry.NotConsented;
import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.util.RequiredPolicies.Covered;
import care.smith.fts.util.RequiredPolicies.Faulty;
import care.smith.fts.util.RequiredPolicies.NotCovered;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public record ConsentedPatientExtractor(
    String patientIdentifierSystem,
    RequiredPolicies requiredPolicies,
    Function<Bundle, Optional<String>> patientIdentifierExtractor) {

  public ConsentedPatientExtractor {
    requireNonNull(patientIdentifierSystem, "Patient identifier system cannot be null");
    requireNonNull(requiredPolicies, "Required policies cannot be null");
    requireNonNull(patientIdentifierExtractor, "Patient identifier extractor cannot be null");
  }

  /**
   * The cohort entries of the given bundles, see {@link #toCohortEntry} and {@link #identified}.
   */
  public List<CohortEntry> cohortEntries(Stream<Bundle> bundles) {
    return identified(bundles.map(this::toCohortEntry).toList());
  }

  /**
   * The present cohort entries. Warns once about the patients without an identifier of the
   * configured system, as they hint at a misconfigured identifier system.
   */
  public List<CohortEntry> identified(List<Optional<CohortEntry>> entries) {
    var identified = entries.stream().flatMap(Optional::stream).toList();
    var unidentified = entries.size() - identified.size();
    if (unidentified > 0) {
      log.warn(
          "Ignoring {} patients without an identifier of the configured patient identifier system",
          unidentified);
    }
    return identified;
  }

  /**
   * Classifies the patient of the given bundle for the cohort.
   *
   * @return {@link Consented} if all required policies are consented to, {@link Excluded} if each
   *     missing policy is only covered by a faulty provision, otherwise {@link NotConsented}; empty
   *     if the patient has no identifier
   */
  public Optional<CohortEntry> toCohortEntry(Bundle bundle) {
    return patientIdentifierExtractor.apply(bundle).flatMap(id -> classifyPatient(id, bundle));
  }

  private Optional<CohortEntry> classifyPatient(String patientIdentifier, Bundle bundle) {
    return switch (requiredPolicies.classify(bundle)) {
      case Covered c ->
          Optional.of(
              new Consented(
                  new ConsentedPatient(patientIdentifier, patientIdentifierSystem, c.policies())));
      case Faulty f -> Optional.of(new Excluded(patientIdentifier, f.reason()));
      case NotCovered n -> Optional.of(new NotConsented(patientIdentifier));
    };
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
        .filter(id -> patientIdentifierSystem.equals(id.getSystem()))
        .map(Identifier::getValue)
        .findFirst();
  }
}
