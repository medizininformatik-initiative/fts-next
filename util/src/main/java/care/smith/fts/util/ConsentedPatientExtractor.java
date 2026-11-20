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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Patient;
import org.slf4j.event.Level;

/**
 * Extracts consented patients from bundles, each holding a patient and its consents.
 *
 * @param patientIdentifierSystem the system used for patient identifiers in the result
 * @param requiredPolicies the policies a patient has to consent to
 * @param sourceIdentifierSystem the system of the patient identifiers in the bundles
 * @param preferredIdentifiers the identifiers to read if the patient has one of them, e.g. the
 *     requested ones
 */
@Slf4j
public record ConsentedPatientExtractor(
    String patientIdentifierSystem,
    RequiredPolicies requiredPolicies,
    String sourceIdentifierSystem,
    Set<String> preferredIdentifiers) {

  public ConsentedPatientExtractor {
    requireNonNull(patientIdentifierSystem, "Patient identifier system cannot be null");
    requireNonNull(requiredPolicies, "Required policies cannot be null");
    requireNonNull(sourceIdentifierSystem, "Source identifier system cannot be null");
    preferredIdentifiers = Set.copyOf(preferredIdentifiers);
  }

  public ConsentedPatientExtractor(
      String patientIdentifierSystem,
      RequiredPolicies requiredPolicies,
      String sourceIdentifierSystem) {
    this(patientIdentifierSystem, requiredPolicies, sourceIdentifierSystem, Set.of());
  }

  /**
   * This extractor, reading a patient by one of the given identifiers if it has several of the
   * source system, so a requested patient is not reported as missing.
   */
  public ConsentedPatientExtractor preferring(Collection<String> identifiers) {
    return new ConsentedPatientExtractor(
        patientIdentifierSystem, requiredPolicies, sourceIdentifierSystem, Set.copyOf(identifiers));
  }

  /**
   * The identifier of the bundle's patient, see {@link #getPatientIdentifier(String, Set, Bundle)}.
   */
  public Optional<String> patientIdentifier(Bundle bundle) {
    return getPatientIdentifier(sourceIdentifierSystem, preferredIdentifiers, bundle);
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
  public <T> List<T> identified(List<Optional<T>> entries) {
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
    return toCohortEntry(bundle, Level.WARN);
  }

  /**
   * Like {@link #toCohortEntry(Bundle)}, logging provisions that cannot be evaluated at the given
   * level.
   */
  public Optional<CohortEntry> toCohortEntry(Bundle bundle, Level skippedLevel) {
    return patientIdentifier(bundle).flatMap(id -> classifyPatient(id, bundle, skippedLevel));
  }

  private Optional<CohortEntry> classifyPatient(
      String patientIdentifier, Bundle bundle, Level skippedLevel) {
    return switch (requiredPolicies.classify(bundle, skippedLevel)) {
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
    return getPatientIdentifier(patientIdentifierSystem, Set.of(), bundle);
  }

  /**
   * The value of an identifier of the bundle's patient with the given system: a preferred one if
   * the patient has one, otherwise the first.
   */
  public static Optional<String> getPatientIdentifier(
      String patientIdentifierSystem, Set<String> preferred, Bundle bundle) {
    var values =
        typedResourceStream(bundle, Patient.class)
            .flatMap(p -> p.getIdentifier().stream())
            .filter(id -> patientIdentifierSystem.equals(id.getSystem()))
            .map(Identifier::getValue)
            .toList();
    return values.stream()
        .filter(preferred::contains)
        .findFirst()
        .or(() -> values.stream().findFirst());
  }
}
