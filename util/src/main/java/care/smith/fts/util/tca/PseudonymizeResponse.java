package care.smith.fts.util.tca;

import java.time.Duration;
import java.util.Map;

public record PseudonymizeResponse(Map<String, String> idMap, Duration dateShiftValue) {}
