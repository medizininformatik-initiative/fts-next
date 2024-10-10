package care.smith.fts.tca.deidentification;

import static com.google.common.hash.Hashing.sha256;
import static java.nio.charset.StandardCharsets.UTF_8;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Random;

public interface DateShiftUtil {

  static DateShifts generate(@NotBlank String seed, @NotNull Duration maxDateShift) {
    var random = new Random(sha256().hashString(seed, UTF_8).padToLong());
    var shiftBy = maxDateShift.toMillis();

    var cdDateShift = Duration.ofMillis(random.nextLong(-shiftBy, shiftBy));
    var rdDateShift = Duration.ofMillis(random.nextLong(-shiftBy, shiftBy));

    return new DateShifts(cdDateShift, rdDateShift.minus(cdDateShift));
  }

  record DateShifts(Duration cdDateShift, Duration rdDateShift) {}
}
