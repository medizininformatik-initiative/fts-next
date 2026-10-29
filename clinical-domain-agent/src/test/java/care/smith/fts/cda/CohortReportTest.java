package care.smith.fts.cda;

import static care.smith.fts.cda.CohortReport.NO_CONSENT_COVERING;
import static care.smith.fts.cda.CohortReport.NO_CONSENT_FOUND;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.api.CohortEntry.Consented;
import care.smith.fts.api.CohortEntry.Excluded;
import care.smith.fts.api.CohortEntry.NotConsented;
import care.smith.fts.api.ConsentedPatient;
import java.util.List;
import org.junit.jupiter.api.Test;

class CohortReportTest {

  private static Consented consented(String identifier) {
    return new Consented(new ConsentedPatient(identifier, "system"));
  }

  @Test
  void consentedPatientIsNotReported() {
    var report = new CohortReport(List.of());

    report.add(consented("p1"));

    assertThat(report.excluded()).isEmpty();
  }

  @Test
  void excludedPatientIsReported() {
    var report = new CohortReport(List.of());

    report.add(new Excluded("p1", "Faulty"));

    assertThat(report.excluded()).containsExactly(new Excluded("p1", "Faulty"));
  }

  @Test
  void excludedPatientIsReportedOnceWithFirstReason() {
    var report = new CohortReport(List.of());

    report.add(new Excluded("p1", "First"));
    report.add(new Excluded("p1", "Second"));

    assertThat(report.excluded()).containsExactly(new Excluded("p1", "First"));
  }

  @Test
  void excludedPatientConsentedLaterIsNotReported() {
    var report = new CohortReport(List.of());

    report.add(new Excluded("p1", "Faulty"));
    report.add(consented("p1"));

    assertThat(report.excluded()).isEmpty();
  }

  @Test
  void excludedPatientsAreReportedInOrderOfArrival() {
    var report = new CohortReport(List.of());

    report.add(new Excluded("p2", "Faulty"));
    report.add(new Excluded("p1", "Faulty"));

    assertThat(report.excluded()).extracting(Excluded::identifier).containsExactly("p2", "p1");
  }

  @Test
  void notConsentedPatientIsNotReportedWhenSelectingAllPatients() {
    var report = new CohortReport(List.of());

    report.add(new NotConsented("p1"));

    assertThat(report.excluded()).isEmpty();
  }

  @Test
  void requestedNotConsentedPatientIsReportedAsNotCovering() {
    var report = new CohortReport(List.of("p1"));

    report.add(new NotConsented("p1"));

    assertThat(report.excluded()).containsExactly(new Excluded("p1", NO_CONSENT_COVERING));
  }

  @Test
  void unrequestedNotConsentedPatientIsNotReported() {
    var report = new CohortReport(List.of("p1"));

    report.add(consented("p1"));
    report.add(new NotConsented("p2"));

    assertThat(report.excluded()).isEmpty();
  }

  @Test
  void requestedPatientNotFoundIsReportedAsNoConsentFound() {
    var report = new CohortReport(List.of("p1"));

    assertThat(report.excluded()).containsExactly(new Excluded("p1", NO_CONSENT_FOUND));
  }

  @Test
  void requestedPatientIsReportedOnce() {
    var report = new CohortReport(List.of("p1", "p1"));

    assertThat(report.excluded()).containsExactly(new Excluded("p1", NO_CONSENT_FOUND));
  }

  @Test
  void faultyConsentWinsOverNotConsented() {
    var report = new CohortReport(List.of("p1"));

    report.add(new NotConsented("p1"));
    report.add(new Excluded("p1", "Faulty"));

    assertThat(report.excluded()).containsExactly(new Excluded("p1", "Faulty"));
  }

  @Test
  void requestedPatientsFollowExclusionsInRequestOrder() {
    var report = new CohortReport(List.of("p3", "p1", "p2"));

    report.add(new Excluded("p2", "Faulty"));
    report.add(new NotConsented("p1"));

    assertThat(report.excluded())
        .containsExactly(
            new Excluded("p2", "Faulty"),
            new Excluded("p3", NO_CONSENT_FOUND),
            new Excluded("p1", NO_CONSENT_COVERING));
  }
}
