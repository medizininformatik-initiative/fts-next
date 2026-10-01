package care.smith.fts.util.selfassessment;

import static care.smith.fts.util.selfassessment.Status.DEGRADED;
import static care.smith.fts.util.selfassessment.Status.DOWN;
import static care.smith.fts.util.selfassessment.Status.SKIPPED;
import static care.smith.fts.util.selfassessment.Status.UP;
import static care.smith.fts.util.selfassessment.StatusAggregator.worstOf;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class StatusAggregatorTest {

  @Test
  void emptyStreamIsUp() {
    assertThat(worstOf(Stream.empty())).isEqualTo(UP);
  }

  @Test
  void allUpIsUp() {
    assertThat(worstOf(Stream.of(UP, UP))).isEqualTo(UP);
  }

  @Test
  void upMixedWithSkippedIsUp() {
    assertThat(worstOf(Stream.of(UP, SKIPPED))).isEqualTo(UP);
  }

  @Test
  void anyDegradedWinsOverUp() {
    assertThat(worstOf(Stream.of(UP, DEGRADED, SKIPPED))).isEqualTo(DEGRADED);
  }

  @Test
  void anyDownWinsOverDegraded() {
    assertThat(worstOf(Stream.of(UP, DEGRADED, DOWN))).isEqualTo(DOWN);
  }

  @Test
  void allSkippedIsSkipped() {
    assertThat(worstOf(Stream.of(SKIPPED, SKIPPED))).isEqualTo(SKIPPED);
  }
}
