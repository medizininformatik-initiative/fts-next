package care.smith.fts.util.tca;

import java.time.Duration;

public record PseudonymizeResponse(IDMap idMap, Duration dateShiftValue) {}
