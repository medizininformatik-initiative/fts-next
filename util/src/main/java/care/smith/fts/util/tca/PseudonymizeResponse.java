package care.smith.fts.util.tca;

import java.time.Duration;
import java.util.Map;

public record PseudonymizeResponse(
    String tIDMapName, Map<String, String> originalToTransportIDMap, Duration dateShiftValue) {}
