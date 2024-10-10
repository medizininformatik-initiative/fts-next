package care.smith.fts.tca.deidentification;

import static java.time.Duration.ofMillis;

import com.google.common.hash.Hashing;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Random;

public interface DateShiftUtil {

  static Duration generate(@NotBlank String salt, @NotNull Duration maxDateShift) {
    var seed = Hashing.sha256().hashString(salt, StandardCharsets.UTF_8).padToLong();
    var random = new Random(seed);
    var shiftBy = maxDateShift.toMillis();
    return ofMillis(random.nextLong(-shiftBy, shiftBy));
  }
}
