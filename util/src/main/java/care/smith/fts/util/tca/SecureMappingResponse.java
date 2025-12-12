package care.smith.fts.util.tca;

import static java.time.Duration.ofMillis;
import static java.util.Map.copyOf;
import static java.util.Objects.requireNonNull;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public record SecureMappingResponse(
    @NotNull Map<String, String> tidPidMap, @NotNull Duration dateShiftBy) {

  private static final String DATE_SHIFT_KEY = "dateShiftMillis";

  public SecureMappingResponse {
    tidPidMap = copyOf(tidPidMap);
    requireNonNull(dateShiftBy, "dateShiftBy cannot be null");
  }

  /**
   * Creates a SecureMappingResponse by extracting the date shift value from the provided map. The
   * dateShiftMillis key is removed from the map during processing.
   *
   * @param sourceMap the map containing tid-pid mappings and dateShiftMillis
   * @return a new SecureMappingResponse instance
   * @throws IllegalArgumentException if dateShiftMillis is missing, invalid, or negative
   */
  public static SecureMappingResponse buildResolveResponse(Map<String, String> sourceMap) {
    requireNonNull(sourceMap, "sourceMap cannot be null");

    var mutableMap = new HashMap<>(sourceMap);
    return Optional.ofNullable(mutableMap.remove(DATE_SHIFT_KEY))
        .map(SecureMappingResponse::parseDateShiftValue)
        .map(dateShiftValue -> new SecureMappingResponse(mutableMap, dateShiftValue))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Missing required '" + DATE_SHIFT_KEY + "' in mapping data"));
  }

  private static Duration parseDateShiftValue(String value) {
    try {
      return ofMillis(Long.parseLong(value));
    } catch (NumberFormatException e) {
      log.error("Failed to parse dateShiftMillis value: '{}'", value, e);
      throw new IllegalArgumentException("Invalid dateShiftMillis value: '" + value + "'", e);
    }
  }
}
