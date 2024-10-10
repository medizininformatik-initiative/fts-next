package care.smith.fts.util.deidentifhir;

import de.ume.deidentifhir.util.ShiftDateProvider;
import java.time.Duration;

public class DateShiftingProvider implements ShiftDateProvider {
  Duration shiftedDate;

  public DateShiftingProvider(Duration shiftedDate) {
    this.shiftedDate = shiftedDate;
  }

  @Override
  public Long getDateShiftingValueInMillis(String key) {
    return shiftedDate.toMillis();
  }
}
