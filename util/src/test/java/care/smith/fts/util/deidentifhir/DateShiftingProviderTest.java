package care.smith.fts.util.deidentifhir;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DateShiftingProviderTest {

  @Test
  void returnsMillisForPositiveDuration() {
    var duration = Duration.ofDays(5);
    var provider = new DateShiftingProvider(duration);

    assertThat(provider.getDateShiftingValueInMillis("any-key")).isEqualTo(duration.toMillis());
  }

  @Test
  void returnsMillisForNegativeDuration() {
    var duration = Duration.ofDays(-10);
    var provider = new DateShiftingProvider(duration);

    assertThat(provider.getDateShiftingValueInMillis("any-key")).isEqualTo(duration.toMillis());
  }

  @Test
  void returnsZeroForZeroDuration() {
    var provider = new DateShiftingProvider(Duration.ZERO);

    assertThat(provider.getDateShiftingValueInMillis("any-key")).isZero();
  }

  @Test
  void ignoresKeyParameter() {
    var duration = Duration.ofHours(3);
    var provider = new DateShiftingProvider(duration);

    assertThat(provider.getDateShiftingValueInMillis("key-1"))
        .isEqualTo(provider.getDateShiftingValueInMillis("key-2"))
        .isEqualTo(provider.getDateShiftingValueInMillis(""))
        .isEqualTo(provider.getDateShiftingValueInMillis(null));
  }

  @Test
  void handlesLargeDuration() {
    var duration = Duration.ofDays(365 * 100);
    var provider = new DateShiftingProvider(duration);

    assertThat(provider.getDateShiftingValueInMillis("any-key")).isEqualTo(duration.toMillis());
  }
}
