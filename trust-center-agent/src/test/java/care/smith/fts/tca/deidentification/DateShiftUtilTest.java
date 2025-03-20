package care.smith.fts.tca.deidentification;

import static org.assertj.core.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@Slf4j
class DateShiftUtilTest {

  @Test
  void testDeterministicShiftForSameSeed() {
    // Given the same seed, the shifts should be the same
    var seed = "patient123";
    var maxShift = Duration.ofDays(365);
    var keepDaytime = false;
    var keepWeekdayAndDaytime = false;

    var shifts1 =
        DateShiftUtil.generate(seed, maxShift, keepDaytime, keepWeekdayAndDaytime);
    var shifts2 =
        DateShiftUtil.generate(seed, maxShift, keepDaytime, keepWeekdayAndDaytime);

    assertThat(shifts1.cdDateShift()).isEqualTo(shifts2.cdDateShift());
    assertThat(shifts1.rdDateShift()).isEqualTo(shifts2.rdDateShift());
  }

  @Test
  void testDifferentShiftsForDifferentSeeds() {
    var maxShift = Duration.ofDays(365);
    var keepDaytime = false;
    var keepWeekdayAndDaytime = false;

    var shifts1 =
        DateShiftUtil.generate("patient123", maxShift, keepDaytime, keepWeekdayAndDaytime);
    var shifts2 =
        DateShiftUtil.generate("patient456", maxShift, keepDaytime, keepWeekdayAndDaytime);

    assertThat(shifts1.cdDateShift()).isNotEqualTo(shifts2.cdDateShift());
  }

  @Test
  void testShiftsWithinMaxRange() {
    var seed = "patient123";
    var maxShift = Duration.ofDays(100);
    var keepDaytime = false;
    var keepWeekdayAndDaytime = false;

    var shifts =
        DateShiftUtil.generate(seed, maxShift, keepDaytime, keepWeekdayAndDaytime);

    assertThat(Math.abs(shifts.cdDateShift().toMillis())).isLessThanOrEqualTo(maxShift.toMillis());
    assertThat(Math.abs(shifts.cdDateShift().plus(shifts.rdDateShift()).toMillis()))
        .isLessThanOrEqualTo(maxShift.toMillis());
  }

  @Test
  void testKeepDaytime() {
    var seed = "patient123";
    var maxShift = Duration.ofDays(365);
    var keepDaytime = true;
    var keepWeekdayAndDaytime = false;

    var shifts =
        DateShiftUtil.generate(seed, maxShift, keepDaytime, keepWeekdayAndDaytime);

    assertThat(shifts.rdDateShift().plus(shifts.cdDateShift()).toMillis() % (24 * 60 * 60 * 1000)).isEqualTo(0);
  }

  @Test
  void testKeepWeekdayAndDaytime() {
    var seed = "patient123";
    var maxShift = Duration.ofDays(15);
    var keepDaytime = false;
    var keepWeekdayAndDaytime = true;

    var shifts =
        DateShiftUtil.generate(seed, maxShift, keepDaytime, keepWeekdayAndDaytime);

    assertThat(shifts.rdDateShift().plus(shifts.cdDateShift()).toMillis() % (7 * 24 * 60 * 60 * 1000)).isEqualTo(0);
  }

  @ParameterizedTest
  @CsvSource({
    "100, false, false, 0",
    "100, true, false, -24085016",
    "100, false, true, -283285016",
    "365, false, false, 0",
    "365, true, false, -24085016",
    "365, false, true, -456085016"
  })
  void testVariousParameterCombinations(
      int maxShiftDays, boolean keepDaytime, boolean keepWeekdayAndDaytime, Long expectedMillis) {
    var seed = "patient123";
    var maxShift = Duration.ofDays(maxShiftDays);

    var shifts =
        DateShiftUtil.generate(seed, maxShift, keepDaytime, keepWeekdayAndDaytime);

    assertThat(shifts.cdDateShift()).isNotNull();
    assertThat(shifts.rdDateShift()).isNotNull();
    assertThat(Math.abs(shifts.cdDateShift().toMillis())).isLessThanOrEqualTo(maxShift.toMillis());

    if (keepWeekdayAndDaytime) {
      assertThat(shifts.rdDateShift().toMillis() % (7 * 24 * 60 * 60 * 1000)).isEqualTo(expectedMillis);
    } else if (keepDaytime) {
      assertThat(shifts.rdDateShift().toMillis() % (24 * 60 * 60 * 1000)).isEqualTo(expectedMillis);
    }
  }

  @Test
  void testShiftCorrectlyApplied() {
    var seed = "patient123";
    var maxShift = Duration.ofDays(100);
    var keepDaytime = false;
    var keepWeekdayAndDaytime = false;

    var shifts =
        DateShiftUtil.generate(seed, maxShift, keepDaytime, keepWeekdayAndDaytime);

    var originalDate = Instant.now();
    var cdShiftedDate = originalDate.plus(shifts.cdDateShift());
    var rdShiftedDate = cdShiftedDate.plus(shifts.rdDateShift());

    assertThat(cdShiftedDate).isEqualTo(originalDate.plus(shifts.cdDateShift()));
    assertThat(rdShiftedDate).isEqualTo(cdShiftedDate.plus(shifts.rdDateShift()));
  }

  @Test
  void testDistribution() {
    var sampleSize = 1000000;
    var maxShift = Duration.ofDays(14);
    var cdShifts = new HashSet<>();
    var rdShifts = new HashSet<>();
    var summedShifts = new HashSet<>();

    for (int i = 0; i < sampleSize; i++) {
      var seed = "patient" + i;
      var shifts = DateShiftUtil.generate(seed, maxShift, false, false);
      cdShifts.add(shifts.cdDateShift().toMillis());
      rdShifts.add(shifts.rdDateShift().toMillis());
      summedShifts.add(shifts.cdDateShift().plus(shifts.rdDateShift()).toMillis());
    }

    assertThat(cdShifts)
        .hasSizeGreaterThan((int) (sampleSize * 0.9));
    assertThat(rdShifts).hasSizeGreaterThan((int) (sampleSize * 0.9));
    assertThat(summedShifts).hasSizeGreaterThan((int) (sampleSize * 0.9));
  }
}
