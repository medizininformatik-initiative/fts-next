package care.smith.fts.tca.deidentification;

import care.smith.fts.util.tca.ShiftedDates;

import java.time.Duration;
import java.util.Set;

public interface ShiftedDatesProvider {
  ShiftedDates generateDateShift(Set<String> ids, Duration dateShiftBy);
}
