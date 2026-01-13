package care.smith.fts.util.tca;

import static java.util.Map.copyOf;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;

/**
 * Response from TCA containing resolved mappings for RDA.
 *
 * @param tidPidMap mapping from transport ID to pseudonym/hashed ID
 * @param dateShiftMap mapping from original date (ISO-8601) to shifted date (ISO-8601)
 */
public record SecureMappingResponse(
    @NotNull Map<String, String> tidPidMap, @NotNull Map<String, String> dateShiftMap) {

  private static final String DATE_SHIFT_PREFIX = "ds:";

  public SecureMappingResponse {
    tidPidMap = copyOf(tidPidMap);
    dateShiftMap = copyOf(dateShiftMap);
  }

  /**
   * Creates a SecureMappingResponse by extracting date shift entries (keys starting with "ds:")
   * from the provided map. These entries are separated into dateShiftMap, while the remaining
   * entries form tidPidMap.
   *
   * @param sourceMap the map containing tid-pid mappings and date shift mappings
   * @return a new SecureMappingResponse instance
   */
  public static SecureMappingResponse buildResolveResponse(Map<String, String> sourceMap) {
    requireNonNull(sourceMap, "sourceMap cannot be null");

    var mutableMap = new HashMap<>(sourceMap);

    var dateShiftMap =
        mutableMap.entrySet().stream()
            .filter(e -> e.getKey().startsWith(DATE_SHIFT_PREFIX))
            .collect(
                toMap(e -> e.getKey().substring(DATE_SHIFT_PREFIX.length()), Map.Entry::getValue));

    mutableMap.entrySet().removeIf(e -> e.getKey().startsWith(DATE_SHIFT_PREFIX));

    return new SecureMappingResponse(mutableMap, dateShiftMap);
  }
}
