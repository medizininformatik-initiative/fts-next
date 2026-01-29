package care.smith.fts.util.tca;

import java.util.Map;

/**
 * Response from TCA containing transport mappings and date shift mappings.
 *
 * @param transferId unique identifier for this transfer session
 * @param transportMapping mapping from original resource ID to transport ID
 * @param dateShiftMapping mapping from original date (ISO-8601) to shifted date (ISO-8601)
 */
public record TransportMappingResponse(
    String transferId,
    Map<String, String> transportMapping,
    Map<String, String> dateShiftMapping) {}
