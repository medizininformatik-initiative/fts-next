package care.smith.fts.util.tca;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;

public record DateShiftingRequest(
    @NotNull(groups = DateShiftingRequest.class) String id,
    @NotNull(groups = DateShiftingRequest.class) Duration dateShift) {}
