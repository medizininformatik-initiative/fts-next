package care.smith.fts.tca.deidentification;

import java.time.Duration;
import reactor.core.publisher.Mono;

public interface ShiftedDatesProvider {
  Mono<Duration> generateDateShift(String id, Duration dateShiftBy);
}
