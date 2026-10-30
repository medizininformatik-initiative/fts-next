package care.smith.fts.cda.impl;

import static care.smith.fts.test.LogRecorder.messages;
import static care.smith.fts.test.LogRecorder.recordLog;
import static org.assertj.core.api.Assertions.assertThat;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.CohortEntry;
import care.smith.fts.api.cda.CohortSelector;
import ch.qos.logback.classic.Level;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExternalCohortSelectorTest {

  private CohortSelector selector;

  @BeforeEach
  void setUp() {
    selector =
        new ExternalCohortSelector()
            .create(new CohortSelector.Config(), new ExternalCohortSelector.Config("system"));
  }

  @Test
  void testConfigType() {
    assertThat(new ExternalCohortSelector().getConfigType()).isNotNull();
  }

  @Test
  void containsExactlyPatientsConfigured() {
    create(selector.selectCohort(List.of("pid-1", "pid-2")).map(CohortEntry::identifier))
        .expectNext("pid-1", "pid-2")
        .verifyComplete();
  }

  @Test
  void logsContainNoPatientIdentifiers() {
    var entries = new ArrayList<CohortEntry>();

    var events =
        recordLog(
            ExternalCohortSelector.class,
            Level.TRACE,
            () -> entries.addAll(selector.selectCohort(List.of("pid-1")).collectList().block()));

    assertThat(entries).hasSize(1);
    var identifier = entries.getFirst().identifier();
    assertThat(messages(events)).isNotEmpty().noneMatch(m -> m.contains(identifier));
  }

  @Test
  void emptyBundleGivesEmptyResult() {
    create(selector.selectCohort(List.of())).verifyComplete();
  }
}
