package care.smith.fts.util.tca;

import static care.smith.fts.util.deidentifhir.DateShiftConstants.DATE_SHIFT_PREFIX;
import static java.util.Map.copyOf;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toMap;

import jakarta.validation.constraints.NotNull;
import java.util.Map;

/**
 * Response from TCA containing resolved mappings for RDA.
 *
 * @param tidPidMap mapping from transport ID to pseudonym/hashed ID
 * @param dateShiftMap mapping from original date (ISO-8601) to shifted date (ISO-8601)
 */
public record SecureMappingResponse(
    @NotNull Map<String, String> tidPidMap, @NotNull Map<String, String> dateShiftMap) {

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

    var partitioned =
        sourceMap.entrySet().stream()
            .collect(partitioningBy(e -> e.getKey().startsWith(DATE_SHIFT_PREFIX)));

    var dateShiftMap =
        partitioned.get(true).stream()
            .collect(
                toMap(e -> e.getKey().substring(DATE_SHIFT_PREFIX.length()), Map.Entry::getValue));

    var tidPidMap =
        partitioned.get(false).stream().collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

    return new SecureMappingResponse(tidPidMap, dateShiftMap);
  }
}
