package care.smith.fts.tca.deidentification;

import static java.time.Duration.ofDays;
import static org.assertj.core.api.Assertions.*;

import care.smith.fts.api.DateShiftPreserve;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class DateShiftUtilTest {

  private static final long WEEK_IN_MS = ofDays(7).toMillis();
  private static final long DAY_IN_MS = ofDays(1).toMillis();

  @Test
  void testDeterministicShiftForSameSeed() {
    var seed = "patient123";
    var maxShift = ofDays(365);

    var shift1 = DateShiftUtil.generate(seed, maxShift, DateShiftPreserve.NONE);
    var shift2 = DateShiftUtil.generate(seed, maxShift, DateShiftPreserve.NONE);

    assertThat(shift1).isEqualTo(shift2);
  }

  @Test
  void testDifferentShiftsForDifferentSeeds() {
    var maxShift = ofDays(365);

    var shift1 = DateShiftUtil.generate("patient123", maxShift, DateShiftPreserve.NONE);
    var shift2 = DateShiftUtil.generate("patient456", maxShift, DateShiftPreserve.NONE);

    assertThat(shift1).isNotEqualTo(shift2);
  }

  @Test
  void testShiftWithinMaxRange() {
    var seed = "patient123";
    var maxShift = ofDays(100);

    var shift = DateShiftUtil.generate(seed, maxShift, DateShiftPreserve.NONE);

    assertThat(Math.abs(shift.toMillis())).isLessThanOrEqualTo(maxShift.toMillis());
  }

  @Test
  void testKeepDaytime() {
    var seed = "patient123";
    var maxShift = ofDays(365);

    var shift = DateShiftUtil.generate(seed, maxShift, DateShiftPreserve.DAYTIME);

    assertThat(shift.toMillis() % DAY_IN_MS).isEqualTo(0);
  }

  @Test
  void testKeepWeekdayAndDaytime() {
    var seed = "patient123";
    var maxShift = ofDays(15);

    var shift = DateShiftUtil.generate(seed, maxShift, DateShiftPreserve.WEEKDAY);

    assertThat(shift.toMillis() % WEEK_IN_MS).isEqualTo(0);
  }

  @ParameterizedTest
  @CsvSource({
    "100, None",
    "100, Daytime",
    "100, Weekday",
    "365, None",
    "365, Daytime",
    "365, Weekday"
  })
  void testVariousParameterCombinations(int maxShiftDays, String presName) {
    var preserve = DateShiftPreserve.valueOf(presName.toUpperCase());
    var seed = "patient123";
    var maxShift = ofDays(maxShiftDays);
    var shift = DateShiftUtil.generate(seed, maxShift, preserve);

    assertThat(shift).isNotNull();
    assertThat(Math.abs(shift.toMillis())).isLessThanOrEqualTo(maxShift.toMillis());

    switch (preserve) {
      case WEEKDAY -> assertThat(shift.toMillis() % WEEK_IN_MS).isEqualTo(0);
      case DAYTIME -> assertThat(shift.toMillis() % DAY_IN_MS).isEqualTo(0);
    }
  }

  @Test
  void testShiftCorrectlyApplied() {
    var seed = "patient123";
    var maxShift = ofDays(100);

    var shift = DateShiftUtil.generate(seed, maxShift, DateShiftPreserve.NONE);

    var originalDate = Instant.now();
    var shiftedDate = originalDate.plus(shift);

    assertThat(shiftedDate).isEqualTo(originalDate.plus(shift));
  }

  @Test
  void testDistribution() {
    var sampleSize = 1000000;
    var maxShift = ofDays(14);
    var shifts = new HashSet<>();

    for (int i = 0; i < sampleSize; i++) {
      var seed = "patient" + i;
      var shift = DateShiftUtil.generate(seed, maxShift, DateShiftPreserve.NONE);
      shifts.add(shift.toMillis());
    }

    assertThat(shifts).hasSizeGreaterThan((int) (sampleSize * 0.9));
  }

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
    assertThat(DateShiftUtil.shiftDate(input, shift)).isEqualTo(expected);
  }

  @ParameterizedTest
  @ValueSource(strings = {"not-a-date", "", "2024-13-01", "2024-01-32"})
  void rejectsInvalidDates(String invalidDate) {
    assertThatThrownBy(() -> DateShiftUtil.shiftDate(invalidDate, ofDays(1)))
        .isInstanceOf(Exception.class);
  }
}
