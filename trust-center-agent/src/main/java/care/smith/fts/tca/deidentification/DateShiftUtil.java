package care.smith.fts.tca.deidentification;

import static com.google.common.hash.Hashing.sha256;
import static java.nio.charset.StandardCharsets.UTF_8;

import care.smith.fts.api.DateShiftPreserve;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Random;

public interface DateShiftUtil {

  static DateShifts generate(
      @NotBlank String seed, @NotNull Duration maxDateShift, @NotNull DateShiftPreserve preserve) {
    var random = new Random(sha256().hashString(seed, UTF_8).padToLong());
    var shiftBy = maxDateShift.toMillis();
    var cdDateShift = Duration.ofMillis(random.nextLong(-shiftBy, shiftBy));

    var rdDateShift =
        switch (preserve) {
          case WEEKDAY -> getPreservedRdDateShift(random, shiftBy, Duration.ofDays(7));
          case DAYTIME -> getPreservedRdDateShift(random, shiftBy, Duration.ofDays(1));
          default -> Duration.ofMillis(random.nextLong(-shiftBy, shiftBy));
        };

    return new DateShifts(cdDateShift, rdDateShift.minus(cdDateShift));
  }

  private static Duration getPreservedRdDateShift(
      Random random, long shiftBy, Duration multipleOf) {
    var n = shiftBy / multipleOf.toMillis();
    shiftBy -= shiftBy % multipleOf.toMillis();
    var ds = random.nextLong(-n, n + 1);
    return Duration.ofMillis(shiftBy * ds / n);
  }

  record DateShifts(Duration cdDateShift, Duration rdDateShift) {}
}
