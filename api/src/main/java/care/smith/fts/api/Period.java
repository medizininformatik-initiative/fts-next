package care.smith.fts.api;

import java.time.ZonedDateTime;

public record Period(ZonedDateTime start, ZonedDateTime end) {
  public static Period of(ZonedDateTime start, ZonedDateTime end) {
    return new Period(start, end);
  }

  public static Period parse(String start, String end) {
    return of(ZonedDateTime.parse(start), ZonedDateTime.parse(end));
  }
}
