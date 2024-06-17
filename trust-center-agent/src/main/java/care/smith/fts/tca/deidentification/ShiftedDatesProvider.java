package care.smith.fts.tca.deidentification;

import care.smith.fts.util.tca.ShiftedDates;
import java.time.Duration;
import java.util.Set;
import reactor.core.publisher.Mono;

public interface ShiftedDatesProvider {
  Mono<ShiftedDates> generateDateShift(Set<String> ids, Duration dateShiftBy);
}
