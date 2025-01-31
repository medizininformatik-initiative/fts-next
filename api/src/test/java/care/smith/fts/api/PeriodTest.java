package care.smith.fts.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import org.junit.jupiter.api.Test;

class PeriodTest {
  @Test
  void parseEmptyStartThrows() {
    assertThrows(
        DateTimeParseException.class,
        () -> Period.parse("", "2007-12-03T10:15:30+01:00[Europe/Paris]"));
  }

  @Test
  void parseEmptyEndThrows() {
    assertThrows(
        DateTimeParseException.class,
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
