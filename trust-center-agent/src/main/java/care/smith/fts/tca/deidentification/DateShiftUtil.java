package care.smith.fts.tca.deidentification;

import static com.google.common.hash.Hashing.sha256;
import static java.nio.charset.StandardCharsets.UTF_8;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Random;

public interface DateShiftUtil {

  static DateShifts generate(
      @NotBlank String seed,
      @NotNull Duration maxDateShift,
      @NotNull boolean keepDaytime,
      @NotNull boolean keepWeekdayAndDaytime) {
    var random = new Random(sha256().hashString(seed, UTF_8).padToLong());
    var shiftBy = maxDateShift.toMillis();
    var cdDateShift = Duration.ofMillis(random.nextLong(-shiftBy, shiftBy));

    var rdDateShift = Duration.ofMillis(random.nextLong(-shiftBy, shiftBy));

    if (keepWeekdayAndDaytime | keepDaytime) {
      var multipleOf = multipleOf(keepDaytime, keepWeekdayAndDaytime);
      var n = shiftBy / multipleOf;
      shiftBy -= shiftBy % multipleOf;
      var ds = random.nextLong(-n, n + 1);
      var x = shiftBy * ds / n;
      rdDateShift = Duration.ofMillis(x);
    }

    return new DateShifts(cdDateShift, rdDateShift.minus(cdDateShift));
  }

  static int multipleOf(boolean keepDaytime, boolean keepWeekdayAndDaytime) {
    if (keepWeekdayAndDaytime) {
      return 7 * 24 * 60 * 60 * 1000;
    } else if (keepDaytime) {
      return 24 * 60 * 60 * 1000;
    } else return 1;
  }

  record DateShifts(Duration cdDateShift, Duration rdDateShift) {}
}
