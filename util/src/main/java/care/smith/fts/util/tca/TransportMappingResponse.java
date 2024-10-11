package care.smith.fts.util.tca;

import java.time.Duration;
import java.util.Map;

public record TransportMappingResponse(
    String transferId, Map<String, String> transportMapping, Duration dateShiftValue) {}
