package care.smith.fts.util.tca;

import care.smith.fts.api.DateShiftPreserve;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Set;

public record TransportMappingRequest(
    @NotNull(groups = TransportMappingRequest.class) String patientId,
    @NotNull(groups = TransportMappingRequest.class) Set<String> resourceIds,
    @NotNull(groups = TransportMappingRequest.class) TCADomains tcaDomains,
    @NotNull(groups = TransportMappingRequest.class) Duration maxDateShift,
    @NotNull(groups = TransportMappingRequest.class) DateShiftPreserve dateShiftPreserve) {}
