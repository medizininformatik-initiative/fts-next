package care.smith.fts.cda;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Stream.concat;

import care.smith.fts.api.CohortEntry;
import care.smith.fts.api.CohortEntry.Consented;
import care.smith.fts.api.CohortEntry.Excluded;
import care.smith.fts.api.CohortEntry.NotConsented;
import care.smith.fts.api.ConsentedPatient;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

/**
 * Collects the entries of a cohort selection, to report the patients it leaves out once the
 * selection completed. A selection may return a patient more than once, e.g. a FHIR server paging
 * over consents returns a patient with each page holding one of its consents, so a patient is
 * reported only if no entry consents it, and only once.
 */
@Slf4j
class CohortReport {

  static final String NO_CONSENT_FOUND = "No consent found";
  static final String NO_CONSENT_COVERING = "No consent covering all required policies";

  private final Set<String> consented = new HashSet<>();
  private final Map<String, String> excludedReasons = new LinkedHashMap<>();
  private final Map<String, String> unselectedReasons = new LinkedHashMap<>();

  /**
   * @param requested the identifiers the transfer was requested for, empty for all patients
   */
  CohortReport(List<String> requested) {
    requested.forEach(id -> unselectedReasons.put(requireNonNull(id), NO_CONSENT_FOUND));
  }

  /**
   * The consented patients of the cohort selection. Hands each patient it leaves out to {@code
   * report} once the selection completes, or each patient excluded for a faulty consent if it
   * fails, and logs one summary per reason; the log names no patient.
   */
  static Flux<ConsentedPatient> reportedCohort(
      String processId,
      Flux<? extends CohortEntry> selection,
      List<String> requested,
      Consumer<Excluded> report) {
    var cohortReport = new CohortReport(requested);
    return selection
        .doOnNext(cohortReport::add)
        .doOnComplete(() -> report(processId, cohortReport.excluded(), report))
        .doOnError(e -> report(processId, cohortReport.faulty(), report))
        .ofType(Consented.class)
        .map(Consented::patient);
  }

  private static void report(String processId, List<Excluded> excluded, Consumer<Excluded> report) {
    excluded.forEach(report);
    excluded.stream()
        .collect(groupingBy(Excluded::reason, LinkedHashMap::new, counting()))
        .forEach((reason, count) -> {
          var msg = "[Process {}] Patients excluded from cohort: {} ({})";
          log.warn(msg, processId, reason, count);
        });
  }

  synchronized void add(CohortEntry entry) {
    switch (entry) {
      case Consented c -> consented.add(c.identifier());
      case Excluded e -> excludedReasons.putIfAbsent(e.identifier(), e.reason());
      case NotConsented n ->
          unselectedReasons.computeIfPresent(n.identifier(), (id, r) -> NO_CONSENT_COVERING);
    }
  }

  /**
   * The patients to report: each patient excluded for a faulty consent, then each requested patient
   * the selection did not consent or exclude.
   */
  synchronized List<Excluded> excluded() {
    var unselected =
        unselectedReasons.entrySet().stream()
            .filter(e -> !consented.contains(e.getKey()))
            .filter(e -> !excludedReasons.containsKey(e.getKey()))
            .map(e -> new Excluded(e.getKey(), e.getValue()));
    return concat(faulty().stream(), unselected).toList();
  }

  /**
   * The patients excluded for a faulty consent and not consented by another entry. Unlike {@link
   * #excluded()}, it names no requested patient missing from the selection, so it can be reported
   * when the selection fails before returning all entries.
   */
  synchronized List<Excluded> faulty() {
    return excludedReasons.entrySet().stream()
        .filter(e -> !consented.contains(e.getKey()))
        .map(e -> new Excluded(e.getKey(), e.getValue()))
        .toList();
  }
}
