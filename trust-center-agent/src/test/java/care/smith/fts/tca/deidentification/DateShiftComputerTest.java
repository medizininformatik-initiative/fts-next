package care.smith.fts.tca.deidentification;

import static java.time.Duration.ofDays;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class DateShiftComputerTest {

  private final DateShiftComputer computer = new DateShiftComputer();

  static Stream<Arguments> dateShiftCases() {
    return Stream.of(
        // date only
        Arguments.of("2024-01-15", ofDays(5), "2024-01-20"),
        Arguments.of("2024-01-15", ofDays(-10), "2024-01-05"),
        // dateTime with UTC
        Arguments.of("2024-01-15T10:30:00Z", ofDays(5), "2024-01-20T10:30:00+00:00"),
        // dateTime with offset
        Arguments.of("2024-01-15T10:30:00+02:00", ofDays(5), "2024-01-20T10:30:00+02:00"),
        // year-month
        Arguments.of("2024-01", ofDays(35), "2024-02"),
        // year only
        Arguments.of("2024", ofDays(400), "2025"),
        // leap year boundary
        Arguments.of("2024-02-28", ofDays(1), "2024-02-29"),
        Arguments.of("2024-02-29", ofDays(1), "2024-03-01"),
        // zero shift
        Arguments.of("2024-06-15", Duration.ZERO, "2024-06-15"),
        // dateTime with seconds precision
        Arguments.of("2024-03-10T08:00:00Z", ofDays(1), "2024-03-11T08:00:00+00:00"));
  }

  @ParameterizedTest
  @MethodSource("dateShiftCases")
  void shiftsDateAndPreservesPrecision(String input, Duration shift, String expected) {
    assertThat(computer.shiftDate(input, shift)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-a-date", "", "2024-13-01", "2024-01-32"})
  void rejectsInvalidDates(String invalidDate) {
    assertThatThrownBy(() -> computer.shiftDate(invalidDate, ofDays(1)))
        .isInstanceOf(Exception.class);
  }
}
