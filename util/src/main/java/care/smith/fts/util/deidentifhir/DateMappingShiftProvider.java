package care.smith.fts.util.deidentifhir;

import de.ume.deidentifhir.util.ShiftDateProvider;
import java.time.Duration;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

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
      throw new IllegalStateException(
          "No shifted date found for original date: " + originalDateString);
    }
    var original = parseDateTime(originalDateString);
    var shifted = parseDateTime(shiftedDateString);
    return Duration.between(original, shifted).toMillis();
  }

  private ZonedDateTime parseDateTime(String dateString) {
    return tryParse(() -> ZonedDateTime.parse(dateString, DateTimeFormatter.ISO_DATE_TIME))
        .or(
            () ->
                tryParse(
                    () ->
                        LocalDate.parse(dateString, DateTimeFormatter.ISO_DATE)
                            .atStartOfDay(ZoneOffset.UTC)))
        .or(() -> tryParse(() -> YearMonth.parse(dateString).atDay(1).atStartOfDay(ZoneOffset.UTC)))
        .or(() -> tryParse(() -> Year.parse(dateString).atDay(1).atStartOfDay(ZoneOffset.UTC)))
        .orElseThrow(
            () -> new DateTimeParseException("Cannot parse date: " + dateString, dateString, 0));
  }

  private static Optional<ZonedDateTime> tryParse(Supplier<ZonedDateTime> parser) {
    try {
      return Optional.of(parser.get());
    } catch (DateTimeParseException e) {
      return Optional.empty();
    }
  }
}
