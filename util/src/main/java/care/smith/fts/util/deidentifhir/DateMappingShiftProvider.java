package care.smith.fts.util.deidentifhir;

import de.ume.deidentifhir.util.ShiftDateProvider;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * A ShiftDateProvider that calculates the shift duration from a pre-computed date mapping. Given an
 * original date, it looks up the shifted date in the mapping and returns the difference as the
 * shift value.
 */
public class DateMappingShiftProvider implements ShiftDateProvider {

  private final Map<String, String> dateShiftMapping;

  public DateMappingShiftProvider(Map<String, String> dateShiftMapping) {
    this.dateShiftMapping = dateShiftMapping;
  }

  @Override
  public Long getDateShiftingValueInMillis(String originalDateString) {
    String shiftedDateString = dateShiftMapping.get(originalDateString);
    if (shiftedDateString == null) {
      return 0L;
    }

    try {
      var original = parseDateTime(originalDateString);
      var shifted = parseDateTime(shiftedDateString);
      return Duration.between(original, shifted).toMillis();
    } catch (DateTimeParseException e) {
      return 0L;
    }
  }

  private ZonedDateTime parseDateTime(String dateString) {
    // Try parsing with timezone
    try {
      return ZonedDateTime.parse(dateString, DateTimeFormatter.ISO_DATE_TIME);
    } catch (DateTimeParseException ignored) {
    }

    // Try parsing as date only (add start of day in UTC)
    try {
      return java.time.LocalDate.parse(dateString, DateTimeFormatter.ISO_DATE)
          .atStartOfDay(java.time.ZoneOffset.UTC);
    } catch (DateTimeParseException ignored) {
    }

    // Try parsing as year-month
    try {
      return java.time.YearMonth.parse(dateString).atDay(1).atStartOfDay(java.time.ZoneOffset.UTC);
    } catch (DateTimeParseException ignored) {
    }

    // Try parsing as year only
    try {
      return java.time.Year.parse(dateString).atDay(1).atStartOfDay(java.time.ZoneOffset.UTC);
    } catch (DateTimeParseException e) {
      throw new DateTimeParseException("Cannot parse date: " + dateString, dateString, 0);
    }
  }
}
