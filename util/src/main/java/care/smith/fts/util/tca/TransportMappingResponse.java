package care.smith.fts.util.tca;

/**
 * Response from TCA confirming transport mapping storage.
 *
 * @param transferId unique identifier for this transfer session
 */
public record TransportMappingResponse(String transferId) {}
