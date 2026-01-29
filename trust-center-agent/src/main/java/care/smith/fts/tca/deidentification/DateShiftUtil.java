package care.smith.fts.tca.deidentification;

import static com.google.common.hash.Hashing.sha256;
import static java.nio.charset.StandardCharsets.UTF_8;

import care.smith.fts.api.DateShiftPreserve;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Date;
import java.util.Random;
import org.hl7.fhir.r4.model.DateTimeType;

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
    random.nextLong(); // Skip first value to maintain compatibility with removed cdShift
    var shiftBy = maxDateShift.toMillis();

    return switch (preserve) {
      case WEEKDAY -> getPreservedShift(random, shiftBy, Duration.ofDays(7));
      case DAYTIME -> getPreservedShift(random, shiftBy, Duration.ofDays(1));
      default -> Duration.ofMillis(random.nextLong(-shiftBy, shiftBy));
    };
  }

  private static Duration getPreservedShift(Random random, long maxShiftMs, Duration periodUnit) {
    var periodMs = periodUnit.toMillis();
    var maxPeriods = maxShiftMs / periodMs;
    var randomPeriods = random.nextLong(-maxPeriods, maxPeriods + 1);
    return Duration.ofMillis(randomPeriods * periodMs);
  }

  /**
   * Shifts a date string by the given duration while preserving precision.
   *
   * @param isoDateString original date in ISO-8601 format
   * @param shift duration to shift by (can be negative)
   * @return shifted date in ISO-8601 format with same precision as input
   */
  static String shiftDate(String isoDateString, Duration shift) {
    var dateTime = new DateTimeType(isoDateString);
    var precision = dateTime.getPrecision();
    var originalValue = dateTime.getValue();
    var shiftedValue = new Date(originalValue.getTime() + shift.toMillis());
    return new DateTimeType(shiftedValue, precision, dateTime.getTimeZone()).getValueAsString();
  }
}
