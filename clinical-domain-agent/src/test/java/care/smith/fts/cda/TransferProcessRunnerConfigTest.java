package care.smith.fts.cda;

import static care.smith.fts.cda.TransferProcessRunnerConfig.DEFAULT_COHORT_SELECTION_CONCURRENCY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TransferProcessRunnerConfigTest {

  @Test
  void bindsWhenSendConcurrencyWithinPrefetchWindow() {
    var config = new TransferProcessRunnerConfig(8, 2, 4, 4, Duration.ofDays(1));
    assertThat(config.maxConcurrentPatients()).isEqualTo(8);
    assertThat(config.maxSendConcurrency()).isEqualTo(2);
    assertThat(config.cohortSelectionConcurrency()).isEqualTo(4);
  }

  @Test
  void failsWhenSendConcurrencyExceedsPrefetchWindow() {
    assertThatThrownBy(() -> new TransferProcessRunnerConfig(2, 8, 4, 4, Duration.ofDays(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxSendConcurrency must not exceed runner.maxConcurrentPatients");
  }

  @Test
  void failsWhenMaxConcurrentPatientsIsZeroOrOne() {
    assertThatThrownBy(() -> new TransferProcessRunnerConfig(1, 2, 4, 4, Duration.ofDays(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxConcurrentPatients must be greater than 0");
    assertThatThrownBy(() -> new TransferProcessRunnerConfig(0, 2, 4, 4, Duration.ofDays(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxConcurrentPatients must be greater than 0");
  }

  @Test
  void failsWhenMaxSendConcurrencyIsZeroOrOne() {
    assertThatThrownBy(() -> new TransferProcessRunnerConfig(8, 1, 4, 4, Duration.ofDays(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxSendConcurrency must be greater than 0");
    assertThatThrownBy(() -> new TransferProcessRunnerConfig(8, 0, 4, 4, Duration.ofDays(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxSendConcurrency must be greater than 0");
  }

  @Test
  void cohortSelectionConcurrencyDefaultsWhenNull() {
    var config = new TransferProcessRunnerConfig(8, 2, 4, null, Duration.ofDays(1));
    assertThat(config.cohortSelectionConcurrency()).isEqualTo(DEFAULT_COHORT_SELECTION_CONCURRENCY);
  }

  @Test
  void failsWhenCohortSelectionConcurrencyIsZeroOrNegative() {
    assertThatThrownBy(() -> new TransferProcessRunnerConfig(8, 2, 4, 0, Duration.ofDays(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cohortSelectionConcurrency must be greater than 0");
    assertThatThrownBy(() -> new TransferProcessRunnerConfig(8, 2, 4, -1, Duration.ofDays(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cohortSelectionConcurrency must be greater than 0");
  }
}
