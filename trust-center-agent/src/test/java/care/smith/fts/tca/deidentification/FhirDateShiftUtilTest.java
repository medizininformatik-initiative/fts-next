package care.smith.fts.tca.deidentification;

import static care.smith.fts.tca.deidentification.DateShiftUtil.generate;
import static java.lang.String.valueOf;
import static java.lang.System.currentTimeMillis;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
class DateShiftUtilTest {

  private static final Duration MAX_DATE_SHIFT = Duration.ofDays(14);
  private static final int NUM_RANDOM_SHIFTS = 10000;

  @Test
  void randomCDShiftIsInRange() {
    for (int i = 0; i < NUM_RANDOM_SHIFTS; i++) {
      var dateShiftValues = generate(valueOf(currentTimeMillis()), MAX_DATE_SHIFT);
      assertThat(dateShiftValues.cdDateShift().abs())
          .isLessThanOrEqualTo(MAX_DATE_SHIFT);
    }
  }

  @Test
  void randomRDShiftIsInRange() {
    for (int i = 0; i < NUM_RANDOM_SHIFTS; i++) {
      var dateShiftValues = generate(valueOf(currentTimeMillis()), MAX_DATE_SHIFT);
      assertThat(dateShiftValues.rdDateShift().abs())
          .isLessThanOrEqualTo(MAX_DATE_SHIFT.multipliedBy(2));
    }
  }

  @Test
  void combinedRandomDateShiftIsInRange() {
    for (int i = 0; i < NUM_RANDOM_SHIFTS; i++) {
      var dateShiftValues = generate(valueOf(currentTimeMillis()), MAX_DATE_SHIFT);
      assertThat(dateShiftValues.cdDateShift().plus(dateShiftValues.rdDateShift()).abs())
          .isLessThanOrEqualTo(MAX_DATE_SHIFT);
    }
  }
}
