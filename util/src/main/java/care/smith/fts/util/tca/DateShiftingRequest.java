package care.smith.fts.util.tca;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Set;

public record DateShiftingRequest(
    @NotNull(groups = DateShiftingRequest.class) Set<String> ids,
    @NotNull(groups = DateShiftingRequest.class) Duration dateShift) {}
