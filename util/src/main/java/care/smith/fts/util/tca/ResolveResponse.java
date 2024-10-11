package care.smith.fts.util.tca;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;

public record ResolveResponse(
    @NotNull Map<String, String> tidPidMap, @NotNull Duration dateShiftBy) {}
