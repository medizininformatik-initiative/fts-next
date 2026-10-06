package care.smith.fts.api;

import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * A consented period. A period without end is open-ended, i.e. it has no upper bound.
 *
 * <p>Not a record: a record accessor must return its component's type, so a nullable {@code end}
 * could not be exposed as {@link Optional}.
 */
@ToString
@EqualsAndHashCode
public final class Period {
  @JsonProperty private final ZonedDateTime start;
  @JsonProperty private final ZonedDateTime end;

  @JsonCreator
  private Period(
      @JsonProperty("start") ZonedDateTime start, @JsonProperty("end") ZonedDateTime end) {
    this.start = start;
    this.end = end;
  }

  /**
   * Creates a period from {@code start} to {@code end}.
   *
   * @param start the start of the period
   * @param end the end of the period, or {@code null} if the period is open-ended
   * @return the period
   */
  public static Period of(ZonedDateTime start, ZonedDateTime end) {
    return new Period(start, end);
  }

  /**
   * Creates an open-ended period, i.e. a period without upper bound.
   *
   * @param start the start of the period
   * @return the open-ended period
   */
  public static Period openEnded(ZonedDateTime start) {
    return new Period(start, null);
  }

  /**
   * Parses a bounded period from FHIR dateTime values. Partial dates are widened: a start to the
   * beginning, an end to the end of the given year, month or day.
   *
   * @param start the FHIR dateTime of the start
   * @param end the FHIR dateTime of the end
   * @return the bounded period
   * @throws NullPointerException if {@code start} or {@code end} is {@code null}
   * @throws IllegalArgumentException if {@code start} or {@code end} is empty
   * @throws java.time.format.DateTimeParseException if a value is no valid FHIR dateTime
   */
  public static Period parse(String start, String end) {
    return of(parseFhirDateTime(start, START), parseFhirDateTime(end, END));
  }

  /**
   * Parses an open-ended period from the FHIR dateTime of its start. A partial date is widened to
   * the beginning of the given year, month or day.
   *
   * @param start the FHIR dateTime of the start
   * @return the open-ended period
   * @throws NullPointerException if {@code start} is {@code null}
   * @throws IllegalArgumentException if {@code start} is empty
   * @throws java.time.format.DateTimeParseException if {@code start} is no valid FHIR dateTime
   */
  public static Period parseOpenEnded(String start) {
    return openEnded(parseFhirDateTime(start, START));
  }

  /**
   * @return the start of the period
   */
  public ZonedDateTime start() {
    return start;
  }

  /**
   * @return the end of the period, or empty if the period is open-ended
   */
  public Optional<ZonedDateTime> end() {
    return Optional.ofNullable(end);
  }

  private static ZonedDateTime parseFhirDateTime(String value, DateBoundaryFactory boundary) {
    requireNonNull(value, "FHIR dateTime cannot be null");
    if (value.isEmpty()) throw new IllegalArgumentException("FHIR dateTime cannot be empty");

    if (value.matches("\\d{4}")) {
      return parseYear(value, boundary);
    } else if (value.matches("\\d{4}-\\d{2}")) {
      return parseYearMonth(value, boundary);
    } else if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
      return parseDate(value, boundary);
    } else {
      return ZonedDateTime.parse(value);
    }
  }

  private static ZonedDateTime parseYear(String value, DateBoundaryFactory boundary) {
    int year = Integer.parseInt(value);
    return boundary.create(year);
  }

  private static ZonedDateTime parseYearMonth(String value, DateBoundaryFactory boundary) {
    var ym = YearMonth.parse(value, DateTimeFormatter.ofPattern("yyyy-MM"));
    return boundary.create(ym);
  }

  private static ZonedDateTime parseDate(String value, DateBoundaryFactory boundary) {
    var date = LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
    return boundary.create(date);
  }

  interface DateBoundaryFactory {
    ZonedDateTime create(int year);

    ZonedDateTime create(YearMonth yearMonth);

    ZonedDateTime create(LocalDate date);
  }

  private static final DateBoundaryFactory START =
      new DateBoundaryFactory() {
        @Override
        public ZonedDateTime create(int year) {
          return toZonedDateTime(LocalDate.of(year, 1, 1));
        }

        @Override
        public ZonedDateTime create(YearMonth yearMonth) {
          return toZonedDateTime(yearMonth.atDay(1));
        }

        @Override
        public ZonedDateTime create(LocalDate local) {
          return toZonedDateTime(local);
        }

        private ZonedDateTime toZonedDateTime(LocalDate date) {
          return date.atStartOfDay(ZoneId.systemDefault());
        }
      };

  private static final DateBoundaryFactory END =
      new DateBoundaryFactory() {
        @Override
        public ZonedDateTime create(int year) {
          return toZonedDateTime(LocalDate.of(year, 12, 31));
        }

        @Override
        public ZonedDateTime create(YearMonth yearMonth) {
          return toZonedDateTime(yearMonth.atEndOfMonth());
        }

        @Override
        public ZonedDateTime create(LocalDate local) {
          return toZonedDateTime(local);
        }

        private ZonedDateTime toZonedDateTime(LocalDate date) {
          return date.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault());
        }
      };
}
