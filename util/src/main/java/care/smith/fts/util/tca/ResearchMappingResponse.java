package care.smith.fts.util.tca;

import static java.util.Map.copyOf;
import static java.util.Objects.requireNonNull;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;

public record ResearchMappingResponse(
    @NotNull Map<String, String> tidPidMap, @NotNull Duration dateShiftBy) {

  public ResearchMappingResponse {
    tidPidMap = copyOf(tidPidMap);
    requireNonNull(dateShiftBy, "dateShiftBy cannot be null");
  }
}