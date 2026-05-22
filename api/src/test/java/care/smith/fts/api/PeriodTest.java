package care.smith.fts.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.Test;

class PeriodTest {
  @Test
  void parseEmptyStartThrows() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Period.parse("", "2007-12-03T10:15:30+01:00[Europe/Paris]"));
  }

  @Test
  void parseEmptyEndThrows() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Period.parse("2007-12-03T10:15:30+01:00[Europe/Paris]", ""));
  }

  @Test
  void parseNullStartThrows() {
    assertThrows(
        NullPointerException.class,
        () -> Period.parse(null, "2007-12-03T10:15:30+01:00[Europe/Paris]"));
  }

  @Test
  void parseNullEndThrows() {
    assertThrows(
        NullPointerException.class,
        () -> Period.parse("2007-12-03T10:15:30+01:00[Europe/Paris]", null));
  }

  @Test
  void parseInvalidStartThrows() {
    assertThrows(
        DateTimeParseException.class,
        () -> Period.parse("invalid", "2007-12-03T10:15:30+01:00[Europe/Paris]"));
  }

  @Test
  void parseInvalidEndThrows() {
    assertThrows(
        DateTimeParseException.class,
        () -> Period.parse("2007-12-03T10:15:30+01:00[Europe/Paris]", "invalid"));
  }

  @Test
  void parseSucceeds() {
    assertThat(
            Period.parse(
                "2007-12-03T10:15:30+01:00[Europe/Paris]",
                "2007-12-03T10:15:30+01:00[Europe/Paris]"))
        .isNotNull();
  }

  @Test
  void parseDateOnly() {
    var period = Period.parse("2024-02-23", "2054-01-31");
    assertThat(period.start().toLocalDate()).isEqualTo(LocalDate.of(2024, 2, 23));
    assertThat(period.start().toLocalTime()).isEqualTo(LocalTime.of(0, 0, 0));
    assertThat(period.end().toLocalDate()).isEqualTo(LocalDate.of(2054, 1, 31));
    assertThat(period.end().toLocalTime()).isEqualTo(LocalTime.MAX);
  }

  @Test
  void parseDateOnlyWithWrongSeparatorFallsThroughAndThrows() {
    assertThrows(
        DateTimeParseException.class,
        () -> Period.parse("2024/02/23", "2007-12-03T10:15:30+01:00[Europe/Paris]"));
  }

  @Test
  void parseDateOnlyWithInvalidDateThrows() {
    assertThrows(
        DateTimeParseException.class,
        () -> Period.parse("2024-02-32", "2007-12-03T10:15:30+01:00[Europe/Paris]"));
  }

  @Test
  void parseYearOnly() {
    var period = Period.parse("2024", "2054");
    assertThat(period.start().toLocalDate()).isEqualTo(LocalDate.of(2024, 1, 1));
    assertThat(period.start().toLocalTime()).isEqualTo(LocalTime.of(0, 0, 0));
    assertThat(period.end().toLocalDate()).isEqualTo(LocalDate.of(2054, 12, 31));
    assertThat(period.end().toLocalTime()).isEqualTo(LocalTime.MAX);
  }

  @Test
  void parseYearOnlyWithNonDigitsFallsThroughAndThrows() {
    assertThrows(
        DateTimeParseException.class,
        () -> Period.parse("20a4", "2007-12-03T10:15:30+01:00[Europe/Paris]"));
  }

  @Test
  void parseYearMonthOnly() {
    var period = Period.parse("2024-02", "2054-01");
    assertThat(period.start().toLocalDate()).isEqualTo(LocalDate.of(2024, 2, 1));
    assertThat(period.start().toLocalTime()).isEqualTo(LocalTime.of(0, 0, 0));
    assertThat(period.end().toLocalDate()).isEqualTo(LocalDate.of(2054, 1, 31));
    assertThat(period.end().toLocalTime()).isEqualTo(LocalTime.MAX);
  }

  @Test
  void parseYearMonthOnlyWithWrongSeparatorFallsThroughAndThrows() {
    assertThrows(
        DateTimeParseException.class,
        () -> Period.parse("2024/02", "2007-12-03T10:15:30+01:00[Europe/Paris]"));
  }

  @Test
  void parseYearMonthOnlyWithInvalidMonthThrows() {
    assertThrows(
        DateTimeParseException.class,
        () -> Period.parse("2024-13", "2007-12-03T10:15:30+01:00[Europe/Paris]"));
  }

  @Test
  void parseMixedFormats() {
    var period = Period.parse("2024-02-23", "2054-01-31T10:00:00+02:00");
    assertThat(period.start().toLocalDate()).isEqualTo(LocalDate.of(2024, 2, 23));
    assertThat(period.start().toLocalTime()).isEqualTo(LocalTime.of(0, 0, 0));
    assertThat(period.end()).isEqualTo(ZonedDateTime.parse("2054-01-31T10:00:00+02:00"));
  }

  @Test
  void ofNullStartThrows() {
    assertThat(Period.of(null, ZonedDateTime.now())).isNotNull();
  }

  @Test
  void ofNullEndThrows() {
    assertThat(Period.of(ZonedDateTime.now(), null)).isNotNull();
  }

  @Test
  void ofSucceeds() {
    assertThat(Period.of(ZonedDateTime.now(), ZonedDateTime.now())).isNotNull();
  }
}
