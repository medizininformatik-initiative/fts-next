package care.smith.fts.util.tca;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record TransportIdsRequest(
    @NotBlank(groups = TransportIdsRequest.class) String domain,
    @NotNull(groups = TransportIdsRequest.class) Set<String> ids) {}
