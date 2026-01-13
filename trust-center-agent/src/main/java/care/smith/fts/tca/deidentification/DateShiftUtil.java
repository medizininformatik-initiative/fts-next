package care.smith.fts.tca.deidentification;

import static com.google.common.hash.Hashing.sha256;
import static java.nio.charset.StandardCharsets.UTF_8;

import care.smith.fts.api.DateShiftPreserve;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Random;

public interface DateShiftUtil {

  /**
   * Generates a deterministic date shift based on the provided seed. The shift is constrained based
   * on the preserve option (WEEKDAY preserves day of week, DAYTIME preserves time of day).
   *
   * @param seed deterministic seed for reproducible shifts
   * @param maxDateShift maximum shift duration
   * @param preserve preservation constraint (NONE, WEEKDAY, DAYTIME)
   * @return the date shift duration
   */
  static Duration generate(
      @NotBlank String seed, @NotNull Duration maxDateShift, @NotNull DateShiftPreserve preserve) {
    var random = new Random(sha256().hashString(seed, UTF_8).padToLong());
    var shiftBy = maxDateShift.toMillis();

    return switch (preserve) {
      case WEEKDAY -> getPreservedShift(random, shiftBy, Duration.ofDays(7));
      case DAYTIME -> getPreservedShift(random, shiftBy, Duration.ofDays(1));
      default -> Duration.ofMillis(random.nextLong(-shiftBy, shiftBy));
    };
  }

  private static Duration getPreservedShift(Random random, long shiftBy, Duration multipleOf) {
    var n = shiftBy / multipleOf.toMillis();
    shiftBy -= shiftBy % multipleOf.toMillis();
    var ds = random.nextLong(-n, n + 1);
    return Duration.ofMillis(shiftBy * ds / n);
  }
}
