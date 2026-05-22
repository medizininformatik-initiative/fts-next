package care.smith.fts.api;

import static java.util.Objects.requireNonNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public record Period(ZonedDateTime start, ZonedDateTime end) {
  public static Period of(ZonedDateTime start, ZonedDateTime end) {
    return new Period(start, end);
  }

  public static Period parse(String start, String end) {
    return of(parseFhirDateTime(start, START), parseFhirDateTime(end, END));
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
