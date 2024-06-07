package care.smith.fts.tca.deidentification;

import care.smith.fts.util.tca.DateShiftingRequest;
import care.smith.fts.util.tca.ShiftedDates;

public interface ShiftedDatesProvider {
  ShiftedDates generateShiftedDates(DateShiftingRequest dateShiftingRequest);
}
