package care.smith.fts.cda;

import static java.util.function.Predicate.not;
import static java.util.stream.Stream.concat;

import care.smith.fts.api.CohortEntry;
import care.smith.fts.api.CohortEntry.Consented;
import care.smith.fts.api.CohortEntry.Excluded;
import care.smith.fts.api.CohortEntry.NotConsented;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Collects the entries of a cohort selection, to report the patients it leaves out once the
 * selection completed. A selection may return a patient more than once, e.g. a FHIR server paging
 * over consents returns a patient with each page holding one of its consents, so a patient is
 * reported only if no entry consents it, and only once.
 */
class CohortReport {

  static final String NO_CONSENT_FOUND = "No consent found";
  static final String NO_CONSENT_COVERING = "No consent covering all required policies";

  private final List<String> requested;
  private final Set<String> consented = new HashSet<>();
  private final Set<String> notConsented = new HashSet<>();
  private final Map<String, String> excludedReasons = new LinkedHashMap<>();

  /**
   * @param requested the identifiers the transfer was requested for, empty for all patients
   */
  CohortReport(List<String> requested) {
    this.requested = List.copyOf(requested);
  }

  synchronized void add(CohortEntry entry) {
    switch (entry) {
      case Consented c -> consented.add(c.identifier());
      case Excluded e -> excludedReasons.putIfAbsent(e.identifier(), e.reason());
      case NotConsented n -> notConsented.add(n.identifier());
    }
  }

  /**
   * The patients to report: each patient excluded for a faulty consent, then each requested patient
   * the selection did not consent or exclude.
   */
  synchronized List<Excluded> excluded() {
    var faulty =
        excludedReasons.entrySet().stream()
            .filter(e -> !consented.contains(e.getKey()))
            .map(e -> new Excluded(e.getKey(), e.getValue()));
    var unselected =
        requested.stream()
            .distinct()
            .filter(not(consented::contains))
            .filter(not(excludedReasons::containsKey))
            .map(id -> new Excluded(id, unselectedReason(id)));
    return concat(faulty, unselected).toList();
  }

  private String unselectedReason(String identifier) {
    return notConsented.contains(identifier) ? NO_CONSENT_COVERING : NO_CONSENT_FOUND;
  }
}
