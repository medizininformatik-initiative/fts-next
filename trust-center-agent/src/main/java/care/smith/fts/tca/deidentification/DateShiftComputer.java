package care.smith.fts.tca.deidentification;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import java.time.Duration;
import java.util.Date;
import org.hl7.fhir.r4.model.DateTimeType;
import org.springframework.stereotype.Component;

/**
 * Computes shifted dates while preserving the original precision. Uses HAPI FHIR's date types to
 * parse, shift, and format dates correctly.
 */
@Component
public class DateShiftComputer {

  /**
   * Shifts a date string by the given duration while preserving precision.
   *
   * @param isoDateString original date in ISO-8601 format
   * @param shift duration to shift by (can be negative)
   * @return shifted date in ISO-8601 format with same precision as input
   */
  public String shiftDate(String isoDateString, Duration shift) {
    var dateTime = new DateTimeType(isoDateString);
    var precision = dateTime.getPrecision();
    var originalValue = dateTime.getValue();

    var shiftedValue = new Date(originalValue.getTime() + shift.toMillis());
    var shiftedDateTime = new DateTimeType(shiftedValue, precision, dateTime.getTimeZone());

    return formatWithPrecision(shiftedDateTime, precision);
  }

  private String formatWithPrecision(DateTimeType dateTime, TemporalPrecisionEnum precision) {
    dateTime.setPrecision(precision);
    return dateTime.getValueAsString();
  }
}
