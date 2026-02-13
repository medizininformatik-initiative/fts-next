package care.smith.fts.util.tca;

import jakarta.validation.constraints.NotNull;

/**
 * Response from TCA confirming transport mapping storage.
 *
 * @param transferId unique identifier for this transfer session
 */
public record TransportMappingResponse(
    @NotNull(groups = TransportMappingResponse.class) String transferId) {}
