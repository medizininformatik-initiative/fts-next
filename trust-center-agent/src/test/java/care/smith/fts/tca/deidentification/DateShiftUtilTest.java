package care.smith.fts.tca.deidentification;

import static java.time.Duration.ofDays;
import static org.assertj.core.api.Assertions.*;

import care.smith.fts.api.DateShiftPreserve;
import java.time.Instant;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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
}
