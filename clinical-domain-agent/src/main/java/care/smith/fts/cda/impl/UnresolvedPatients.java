package care.smith.fts.cda.impl;

import static care.smith.fts.util.MinimalConsents.minimalConsents;
import static care.smith.fts.util.fhir.FhirUtils.resourceStream;
import static care.smith.fts.util.fhir.FhirUtils.toBundle;
import static care.smith.fts.util.fhir.FhirUtils.typedResourceStream;
import static java.util.stream.Stream.concat;

import care.smith.fts.api.CohortEntry;
import care.smith.fts.api.CohortEntry.Consented;
import care.smith.fts.api.CohortEntry.Excluded;
import care.smith.fts.util.ConsentedPatientExtractor;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Consent;
import org.slf4j.event.Level;

/**
 * Classifies the patients of the pages of a FHIR search, whose consents may be spread over several
 * pages. A patient is returned as soon as its consents cover all required policies; otherwise it is
 * held back with its consents, reduced to what classification reads, and classified again with the
 * consents of later pages. Patient resources of one page with the same identifier are classified
 * together.
 */
class UnresolvedPatients {

  private final ConsentedPatientExtractor extractor;
  private final Map<String, Unresolved> unresolved = new LinkedHashMap<>();

  private record Unresolved(Bundle bundle, CohortEntry entry) {}

  private record Candidate(Bundle bundle, Level skippedLevel) {}

  UnresolvedPatients(ConsentedPatientExtractor extractor) {
    this.extractor = extractor;
  }

  /**
   * The patients of a page that are consented, merged with the held back consents of earlier pages
   * and with the consents of other patient resources of the page with the same identifier.
   *
   * @param page one bundle per patient resource, each holding the patient and its consents
   */
  synchronized List<CohortEntry> resolve(List<Bundle> page) {
    var candidates = mergedByPatient(page).stream().map(this::withHeldBack).toList();
    var classified = extractor.identified(candidates.stream().map(this::classify).toList());
    return classified.stream().flatMap(c -> consentedOrHoldBack(c).stream()).toList();
  }

  /** The patients still held back once all pages are read, in order of arrival. */
  synchronized List<CohortEntry> remaining() {
    return unresolved.values().stream().map(Unresolved::entry).toList();
  }

  /**
   * The patients held back that the pages read so far exclude for a faulty consent, to report them
   * if a later page cannot be read.
   */
  synchronized List<CohortEntry> excluded() {
    return remaining().stream().filter(Excluded.class::isInstance).toList();
  }

  private List<Bundle> mergedByPatient(List<Bundle> page) {
    var byPatient = new LinkedHashMap<String, Bundle>();
    var unidentified = new ArrayList<Bundle>();
    page.forEach(b ->
        identifier(b)
            .ifPresentOrElse(
                id -> byPatient.merge(id, b, UnresolvedPatients::merge),
                () -> unidentified.add(b)));
    return concat(byPatient.values().stream(), unidentified.stream()).toList();
  }

  /**
   * Logs skipped provisions of a patient merged with held back consents at debug level only, as
   * they were warned about when the patient was first read.
   */
  private Candidate withHeldBack(Bundle bundle) {
    return identifier(bundle)
        .map(unresolved::remove)
        .map(held -> new Candidate(merge(held.bundle(), bundle), Level.DEBUG))
        .orElseGet(() -> new Candidate(bundle, Level.WARN));
  }

  private Optional<Unresolved> classify(Candidate candidate) {
    return extractor
        .toCohortEntry(candidate.bundle(), candidate.skippedLevel())
        .map(entry -> new Unresolved(candidate.bundle(), entry));
  }

  private Optional<CohortEntry> consentedOrHoldBack(Unresolved classified) {
    if (classified.entry() instanceof Consented) {
      return Optional.of(classified.entry());
    }
    var minimal = minimalConsents(extractor.requiredPolicies(), classified.bundle());
    unresolved.put(classified.entry().identifier(), new Unresolved(minimal, classified.entry()));
    return Optional.empty();
  }

  private Optional<String> identifier(Bundle bundle) {
    return extractor.patientIdentifierExtractor().apply(bundle);
  }

  /** Shares the resources, as copying them fails on unreadable dates. */
  private static Bundle merge(Bundle first, Bundle second) {
    return concat(resourceStream(first), typedResourceStream(second, Consent.class))
        .collect(toBundle());
  }
}
