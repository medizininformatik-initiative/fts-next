package care.smith.fts.cda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TransferProcessRunnerConfigTest {

  @Test
  void bindsWhenSendConcurrencyWithinPrefetchWindow() {
    var config =
        new TransferProcessRunnerConfig(8, 2, 4, Duration.ofDays(1), 100, Duration.ofSeconds(2));
    assertThat(config.maxConcurrentPatients()).isEqualTo(8);
    assertThat(config.maxSendConcurrency()).isEqualTo(2);
    assertThat(config.deidentifyBatchSize()).isEqualTo(100);
    assertThat(config.deidentifyBatchTimeout()).isEqualTo(Duration.ofSeconds(2));
  }

  @Test
  void failsWhenSendConcurrencyExceedsPrefetchWindow() {
    assertThatThrownBy(
            () ->
                new TransferProcessRunnerConfig(
                    2, 8, 4, Duration.ofDays(1), 100, Duration.ofSeconds(2)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxSendConcurrency must not exceed runner.maxConcurrentPatients");
  }

  @Test
  void failsWhenMaxConcurrentPatientsIsZeroOrOne() {
    assertThatThrownBy(
            () ->
                new TransferProcessRunnerConfig(
                    1, 2, 4, Duration.ofDays(1), 100, Duration.ofSeconds(2)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxConcurrentPatients must be greater than 0");
    assertThatThrownBy(
            () ->
                new TransferProcessRunnerConfig(
                    0, 2, 4, Duration.ofDays(1), 100, Duration.ofSeconds(2)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxConcurrentPatients must be greater than 0");
  }

  @Test
  void failsWhenMaxSendConcurrencyIsZeroOrOne() {
    assertThatThrownBy(
            () ->
                new TransferProcessRunnerConfig(
                    8, 1, 4, Duration.ofDays(1), 100, Duration.ofSeconds(2)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxSendConcurrency must be greater than 0");
    assertThatThrownBy(
            () ->
                new TransferProcessRunnerConfig(
                    8, 0, 4, Duration.ofDays(1), 100, Duration.ofSeconds(2)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxSendConcurrency must be greater than 0");
  }

  @Test
  void failsWhenDeidentifyBatchSizeIsZeroOrNegative() {
    assertThatThrownBy(
            () ->
                new TransferProcessRunnerConfig(
                    8, 2, 4, Duration.ofDays(1), 0, Duration.ofSeconds(2)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("deidentifyBatchSize must be greater than 0");
    assertThatThrownBy(
            () ->
                new TransferProcessRunnerConfig(
                    8, 2, 4, Duration.ofDays(1), -1, Duration.ofSeconds(2)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("deidentifyBatchSize must be greater than 0");
  }

  @Test
  void failsWhenDeidentifyBatchTimeoutIsNotPositive() {
    assertThatThrownBy(
            () -> new TransferProcessRunnerConfig(8, 2, 4, Duration.ofDays(1), 100, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("deidentifyBatchTimeout must be positive");
    assertThatThrownBy(
            () ->
                new TransferProcessRunnerConfig(
                    8, 2, 4, Duration.ofDays(1), 100, Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("deidentifyBatchTimeout must be positive");
    assertThatThrownBy(
            () -> new TransferProcessRunnerConfig(8, 2, 4, Duration.ofDays(1), 100, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("deidentifyBatchTimeout must be positive");
  }
}
