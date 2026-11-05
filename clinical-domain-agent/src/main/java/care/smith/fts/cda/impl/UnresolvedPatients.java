package care.smith.fts.cda.impl;

import static care.smith.fts.util.MinimalConsents.minimalConsents;
import static care.smith.fts.util.fhir.FhirUtils.resourceStream;
import static care.smith.fts.util.fhir.FhirUtils.toBundle;
import static care.smith.fts.util.fhir.FhirUtils.typedResourceStream;
import static java.util.stream.Stream.concat;

import care.smith.fts.api.CohortEntry;
import care.smith.fts.api.CohortEntry.Consented;
import care.smith.fts.util.ConsentedPatientExtractor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Consent;

/**
 * Classifies the patients of the pages of a FHIR search, whose consents may be spread over several
 * pages. A patient is returned as soon as its consents cover all required policies; otherwise it is
 * held back with its consents, reduced to what classification reads, and classified again with the
 * consents of later pages.
 */
class UnresolvedPatients {

  private final ConsentedPatientExtractor extractor;
  private final Map<String, Unresolved> unresolved = new LinkedHashMap<>();

  private record Unresolved(Bundle bundle, CohortEntry entry) {}

  UnresolvedPatients(ConsentedPatientExtractor extractor) {
    this.extractor = extractor;
  }

  /**
   * The patients of a page that are consented, merged with the held back consents of earlier pages.
   *
   * @param page one bundle per patient, each holding the patient and its consents
   */
  synchronized List<CohortEntry> resolve(List<Bundle> page) {
    var bundles = page.stream().map(this::withHeldBack).toList();
    var classified = extractor.identified(bundles.stream().map(this::classify).toList());
    return classified.stream().flatMap(c -> consentedOrHoldBack(c).stream()).toList();
  }

  /** The patients still held back once all pages are read, in order of arrival. */
  synchronized List<CohortEntry> remaining() {
    return unresolved.values().stream().map(Unresolved::entry).toList();
  }

  private Bundle withHeldBack(Bundle bundle) {
    return extractor
        .patientIdentifierExtractor()
        .apply(bundle)
        .map(unresolved::remove)
        .map(held -> merge(held.bundle(), bundle))
        .orElse(bundle);
  }

  private Optional<Unresolved> classify(Bundle bundle) {
    return extractor.toCohortEntry(bundle).map(entry -> new Unresolved(bundle, entry));
  }

  private Optional<CohortEntry> consentedOrHoldBack(Unresolved classified) {
    if (classified.entry() instanceof Consented) {
      return Optional.of(classified.entry());
    }
    var minimal = minimalConsents(extractor.requiredPolicies(), classified.bundle());
    unresolved.put(classified.entry().identifier(), new Unresolved(minimal, classified.entry()));
    return Optional.empty();
  }

  /** Shares the resources, as copying them fails on unreadable dates. */
  private static Bundle merge(Bundle held, Bundle bundle) {
    return concat(resourceStream(held), typedResourceStream(bundle, Consent.class))
        .collect(toBundle());
  }
}
