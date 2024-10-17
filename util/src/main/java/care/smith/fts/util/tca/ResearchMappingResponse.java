package care.smith.fts.util.tca;

import static java.util.Map.copyOf;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;

public record ResearchMappingResponse(
    @NotNull Map<String, String> tidPidMap, @NotNull Duration dateShiftBy) {

  public ResearchMappingResponse {
    tidPidMap = copyOf(tidPidMap);
    if (dateShiftBy == null) {
      throw new NullPointerException("dateShiftBy cannot be null");
    }
  }
}
