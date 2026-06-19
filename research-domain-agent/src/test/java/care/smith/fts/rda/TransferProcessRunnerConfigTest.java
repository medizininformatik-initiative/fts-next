package care.smith.fts.rda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TransferProcessRunnerConfigTest {

  @Test
  void bindsValidConfiguration() {
    var config = new TransferProcessRunnerConfig(5, 10, Duration.ofMinutes(10));
    assertThat(config.maxConcurrentTransactions()).isEqualTo(5);
    assertThat(config.retryAfterSeconds()).isEqualTo(10);
    assertThat(config.processTtl()).isEqualTo(Duration.ofMinutes(10));
  }

  @Test
  void failsWhenMaxConcurrentTransactionsIsZero() {
    assertThatThrownBy(() -> new TransferProcessRunnerConfig(0, 5, Duration.ofMinutes(10)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxConcurrentTransactions must be greater than or equal to 1");
  }

  @Test
  void failsWhenMaxConcurrentTransactionsIsNegative() {
    assertThatThrownBy(() -> new TransferProcessRunnerConfig(-1, 5, Duration.ofMinutes(10)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxConcurrentTransactions must be greater than or equal to 1");
  }

  @Test
  void failsWhenRetryAfterSecondsIsZero() {
    assertThatThrownBy(() -> new TransferProcessRunnerConfig(5, 0, Duration.ofMinutes(10)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("retryAfterSeconds must be greater than or equal to 1");
  }

  @Test
  void failsWhenRetryAfterSecondsIsNegative() {
    assertThatThrownBy(() -> new TransferProcessRunnerConfig(5, -1, Duration.ofMinutes(10)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("retryAfterSeconds must be greater than or equal to 1");
  }

  @Test
  void failsWhenProcessTtlIsZero() {
    assertThatThrownBy(() -> new TransferProcessRunnerConfig(5, 5, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("processTtl must be a positive duration");
  }

  @Test
  void failsWhenProcessTtlIsNegative() {
    assertThatThrownBy(() -> new TransferProcessRunnerConfig(5, 5, Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("processTtl must be a positive duration");
  }

  @Test
  void failsWhenProcessTtlIsNull() {
    assertThatThrownBy(() -> new TransferProcessRunnerConfig(5, 5, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("processTtl must be a positive duration");
  }
}
