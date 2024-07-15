package care.smith.fts.tca.deidentification;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import reactor.core.publisher.Mono;

public interface ShiftedDatesProvider {
  Mono<Map<String, Duration>> generateDateShift(Set<String> ids, Duration dateShiftBy);
}
