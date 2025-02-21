package care.smith.fts.tca.deidentification;

import static java.time.Duration.ofDays;
import static org.assertj.core.api.Assertions.*;

import care.smith.fts.api.DateShiftPreserve;
import java.time.Instant;
import java.util.HashSet;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Slf4j
class DateShiftUtilTest {

  private static final long WEEK_IN_MS = ofDays(7).toMillis();
  private static final long DAY_IN_MS = ofDays(1).toMillis();

  @Test
  void testDeterministicShiftForSameSeed() {
    // Given the same seed, the shifts should be the same
    var seed = "patient123";
    var maxShift = ofDays(365);

    var shifts1 = DateShiftUtil.generate(seed, maxShift, DateShiftPreserve.NONE);
    var shifts2 = DateShiftUtil.generate(seed, maxShift, DateShiftPreserve.NONE);

    assertThat(shifts1.cdDateShift()).isEqualTo(shifts2.cdDateShift());
    assertThat(shifts1.rdDateShift()).isEqualTo(shifts2.rdDateShift());
  }

  @Test
  void testDifferentShiftsForDifferentSeeds() {
    var maxShift = ofDays(365);

    var shifts1 = DateShiftUtil.generate("patient123", maxShift, DateShiftPreserve.NONE);
    var shifts2 = DateShiftUtil.generate("patient456", maxShift, DateShiftPreserve.NONE);

    assertThat(shifts1.cdDateShift()).isNotEqualTo(shifts2.cdDateShift());
  }

  @Test
  void testShiftsWithinMaxRange() {
    var seed = "patient123";
    var maxShift = ofDays(100);

    var shifts = DateShiftUtil.generate(seed, maxShift, DateShiftPreserve.NONE);

    assertThat(Math.abs(shifts.cdDateShift().toMillis())).isLessThanOrEqualTo(maxShift.toMillis());
    assertThat(Math.abs(shifts.cdDateShift().plus(shifts.rdDateShift()).toMillis()))
        .isLessThanOrEqualTo(maxShift.toMillis());
  }

  @Test
  void testKeepDaytime() {
    var seed = "patient123";
    var maxShift = ofDays(365);

    var shifts = DateShiftUtil.generate(seed, maxShift, DateShiftPreserve.DAYTIME);

    assertThat(shifts.rdDateShift().plus(shifts.cdDateShift()).toMillis() % (24 * 60 * 60 * 1000))
        .isEqualTo(0);
  }

  @Test
  void testKeepWeekdayAndDaytime() {
    var seed = "patient123";
    var maxShift = ofDays(15);

    var shifts = DateShiftUtil.generate(seed, maxShift, DateShiftPreserve.WEEKDAY);

    assertThat(
            shifts.rdDateShift().plus(shifts.cdDateShift()).toMillis() % (7 * 24 * 60 * 60 * 1000))
        .isEqualTo(0);
  }

  @ParameterizedTest
  @CsvSource({
    "100, None, 0",
    "100, Daytime, -24085016",
    "100, Weekday, -283285016",
    "365, None, 0",
    "365, Daytime, 62314984",
    "365, Weekday, -456085016"
  })
  void testVariousParameterCombinations(int maxShiftDays, String presName, long expectedMillis) {
    var preserve = DateShiftPreserve.valueOf(presName.toUpperCase());
    var seed = "patient123";
    var maxShift = ofDays(maxShiftDays);
    var shifts = DateShiftUtil.generate(seed, maxShift, preserve);

    assertThat(shifts.cdDateShift()).isNotNull();
    assertThat(shifts.rdDateShift()).isNotNull();
    assertThat(Math.abs(shifts.cdDateShift().toMillis())).isLessThanOrEqualTo(maxShift.toMillis());

    switch (preserve) {
      case WEEKDAY -> {
        var originalRDShift = shifts.rdDateShift().plus(shifts.cdDateShift());
        // Assert we are shifting by weeks
        assertThat(originalRDShift.toMillis() % WEEK_IN_MS).isEqualTo(0);
        // Regression test
        assertThat(shifts.rdDateShift().toMillis() % WEEK_IN_MS).isEqualTo(expectedMillis);
      }
      case DAYTIME -> {
        var originalRDShift = shifts.rdDateShift().plus(shifts.cdDateShift());
        // Assert we are shifting by days
        assertThat(originalRDShift.toMillis() % DAY_IN_MS).isEqualTo(0);
        // Regression test
        assertThat(shifts.rdDateShift().toMillis() % DAY_IN_MS).isEqualTo(expectedMillis);
      }
    }
  }

  @Test
  void testShiftCorrectlyApplied() {
    var seed = "patient123";
    var maxShift = ofDays(100);

    var shifts = DateShiftUtil.generate(seed, maxShift, DateShiftPreserve.NONE);

    var originalDate = Instant.now();
    var cdShiftedDate = originalDate.plus(shifts.cdDateShift());
    var rdShiftedDate = cdShiftedDate.plus(shifts.rdDateShift());

    assertThat(cdShiftedDate).isEqualTo(originalDate.plus(shifts.cdDateShift()));
    assertThat(rdShiftedDate).isEqualTo(cdShiftedDate.plus(shifts.rdDateShift()));
  }

  @Test
  void testDistribution() {
    var sampleSize = 1000000;
    var maxShift = ofDays(14);
    var cdShifts = new HashSet<>();
    var rdShifts = new HashSet<>();
    var summedShifts = new HashSet<>();

    for (int i = 0; i < sampleSize; i++) {
      var seed = "patient" + i;
      var shifts = DateShiftUtil.generate(seed, maxShift, DateShiftPreserve.NONE);
      cdShifts.add(shifts.cdDateShift().toMillis());
      rdShifts.add(shifts.rdDateShift().toMillis());
      summedShifts.add(shifts.cdDateShift().plus(shifts.rdDateShift()).toMillis());
    }

    assertThat(cdShifts).hasSizeGreaterThan((int) (sampleSize * 0.9));
    assertThat(rdShifts).hasSizeGreaterThan((int) (sampleSize * 0.9));
    assertThat(summedShifts).hasSizeGreaterThan((int) (sampleSize * 0.9));
  }
}
