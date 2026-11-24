package care.smith.fts.cda.impl;

import static care.smith.fts.util.fhir.FhirUtils.resourceStream;
import static care.smith.fts.util.fhir.FhirUtils.toBundle;
import static care.smith.fts.util.fhir.FhirUtils.typedResourceStream;
import static java.util.function.Predicate.not;
import static java.util.stream.Stream.concat;

import care.smith.fts.api.CohortEntry;
import care.smith.fts.api.CohortEntry.Consented;
import care.smith.fts.util.ConsentedPatientExtractor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Consent;

/**
 * Classifies the patients of the pages of a FHIR search over patients, each page holding whole
 * patients with all their consents. Patient resources of one page with the same identifier are
 * classified together. Identified patients without any consent resource are dropped, so a requested
 * patient without consent is reported as without consent found. Consents of any status count, so a
 * patient with only inactive consents is classified and reported as not consented by them. A
 * patient is selected at most once: once consented, later entries for its identifier, e.g. of a
 * duplicate patient resource on a later page, are dropped.
 */
@Slf4j
class SelectedPatients {

  private final ConsentedPatientExtractor extractor;
  private final Set<String> consented = new HashSet<>();
  private long ignored;

  SelectedPatients(ConsentedPatientExtractor extractor) {
    this.extractor = extractor;
  }

  /**
   * The cohort entries of a page, without patients consented on an earlier page.
   *
   * @param page one bundle per patient resource, each holding the patient and its consents
   */
  synchronized List<CohortEntry> select(List<Bundle> page) {
    var candidates = mergedByPatient(page).stream().filter(this::isCandidate).toList();
    var unselected = candidates.stream().filter(not(this::alreadySelected)).toList();
    ignored += candidates.size() - unselected.size();
    var entries = extractor.cohortEntries(unselected.stream());
    entries.stream()
        .filter(Consented.class::isInstance)
        .forEach(e -> consented.add(e.identifier()));
    return entries;
  }

  /** Warns once about the patients dropped as consented on an earlier page, if any. */
  synchronized void warnAboutIgnored() {
    if (ignored > 0) {
      log.warn("Ignoring {} patients already selected on an earlier page", ignored);
    }
  }

  private List<Bundle> mergedByPatient(List<Bundle> page) {
    var byPatient = new LinkedHashMap<String, Bundle>();
    var unidentified = new ArrayList<Bundle>();
    page.forEach(b ->
        extractor
            .patientIdentifier(b)
            .ifPresentOrElse(
                id -> byPatient.merge(id, b, SelectedPatients::merge), () -> unidentified.add(b)));
    return concat(byPatient.values().stream(), unidentified.stream()).toList();
  }

  private boolean alreadySelected(Bundle bundle) {
    return extractor.patientIdentifier(bundle).filter(consented::contains).isPresent();
  }

  /**
   * Whether the patient is classified: if it has a consent, or if it has no identifier, so the
   * extractor counts it in its warning about patients without identifier.
   */
  private boolean isCandidate(Bundle bundle) {
    return hasConsent(bundle) || extractor.patientIdentifier(bundle).isEmpty();
  }

  private static boolean hasConsent(Bundle bundle) {
    return typedResourceStream(bundle, Consent.class).findAny().isPresent();
  }

  /** Shares the resources, as copying them fails on unreadable dates. */
  private static Bundle merge(Bundle first, Bundle second) {
    return concat(resourceStream(first), typedResourceStream(second, Consent.class))
        .collect(toBundle());
  }
}
