package care.smith.fts.util.deidentifhir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DateMappingShiftProviderTest {

  @Test
  void throwsWhenMappingNotFound() {
    var provider = new DateMappingShiftProvider(Map.of());

    assertThatThrownBy(() -> provider.getDateShiftingValueInMillis("2024-01-15"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No shifted date found");
  }

  @Test
  void calculatesShiftForZonedDateTime() {
    var original = "2024-01-15T10:30:00Z";
    var shifted = "2024-01-20T10:30:00Z";
    var provider = new DateMappingShiftProvider(Map.of(original, shifted));

    var expectedShift = Duration.ofDays(5).toMillis();
    assertThat(provider.getDateShiftingValueInMillis(original)).isEqualTo(expectedShift);
  }

  @Test
  void calculatesShiftForLocalDate() {
    var original = "2024-01-15";
    var shifted = "2024-01-25";
    var provider = new DateMappingShiftProvider(Map.of(original, shifted));

    var expectedShift = Duration.ofDays(10).toMillis();
    assertThat(provider.getDateShiftingValueInMillis(original)).isEqualTo(expectedShift);
  }

  @Test
  void calculatesShiftForYearMonth() {
    var original = "2024-01";
    var shifted = "2024-03";
    var provider = new DateMappingShiftProvider(Map.of(original, shifted));

    var expectedShift = Duration.ofDays(60).toMillis();
    assertThat(provider.getDateShiftingValueInMillis(original)).isEqualTo(expectedShift);
  }

  @Test
  void calculatesShiftForYear() {
    var original = "2024";
    var shifted = "2025";
    var provider = new DateMappingShiftProvider(Map.of(original, shifted));

    var expectedShift = Duration.ofDays(366).toMillis();
    assertThat(provider.getDateShiftingValueInMillis(original)).isEqualTo(expectedShift);
  }

  @Test
  void throwsForUnparseableDate() {
    var original = "invalid-date";
    var shifted = "also-invalid";
    var provider = new DateMappingShiftProvider(Map.of(original, shifted));

    assertThatThrownBy(() -> provider.getDateShiftingValueInMillis(original))
        .isInstanceOf(DateTimeParseException.class);
  }
}
