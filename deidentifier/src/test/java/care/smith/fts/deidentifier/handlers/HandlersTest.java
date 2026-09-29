package care.smith.fts.deidentifier.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import care.smith.fts.deidentifier.DeidentifierHandler;
import care.smith.fts.deidentifier.HandlerContext;
import java.util.Optional;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.MarkdownType;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class HandlersTest {

  /** A five-digit code keeps its first three digits; any other code is removed. */
  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({"48149, 481", "1234, ", ", "})
  void generalizePostalCodeKeepsThreeOfFiveDigits(String code, String expected) {
    assertThat(
            Handlers.generalizePostalCode(new StringType(code), HandlerContext.empty())
                .map(StringType::getValue))
        .isEqualTo(Optional.ofNullable(expected));
  }

  /** A date with a day gets day 15; a coarser date stays unchanged. */
  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({"1970-05-12, 1970-05-15", "1970, 1970", "1970-05, 1970-05"})
  void generalizeDateSetsTheDayOfMonthToTheFifteenth(String date, String expected) {
    DateType result =
        Handlers.generalizeDateHandler(new DateType(date), HandlerContext.empty()).orElseThrow();

    assertThat(result.getValueAsString()).isEqualTo(expected);
    // the day of a coarser date stays untouched, even where its string does not show it
    assertThat(result.getValue()).isEqualTo(new DateType(expected).getValue());
  }

  /** DateType accepts these precisions through setPrecision, although no date string has them. */
  @ParameterizedTest
  @EnumSource(
      value = TemporalPrecisionEnum.class,
      names = {"MINUTE", "SECOND", "MILLI"})
  void generalizeDateRejectsAPrecisionFinerThanDay(TemporalPrecisionEnum precision) {
    DateType date = new DateType("1970-05-12");
    date.setPrecision(precision);

    assertThatThrownBy(() -> Handlers.generalizeDateHandler(date, HandlerContext.empty()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unexpected precision for object of type DateType!");
  }

  @Test
  void stringReplacementHandlerReplacesTheValueWithTheConfiguredString() {
    DeidentifierHandler<StringType> handler = Handlers.stringReplacementHandler("PSEUDONYMISIERT");

    StringType result = handler.apply(new StringType("Doe"), HandlerContext.empty()).orElseThrow();

    assertThat(result.getValue()).isEqualTo("PSEUDONYMISIERT");
  }

  /**
   * Annotation.text is a MarkdownType, a subtype of StringType. The result is written back into
   * that field, so it has to keep the class of the element it replaces.
   */
  @Test
  void stringReplacementHandlerKeepsTheClassOfTheElement() {
    DeidentifierHandler<StringType> handler = Handlers.stringReplacementHandler("PSEUDONYMISIERT");

    StringType result =
        handler.apply(new MarkdownType("free text"), HandlerContext.empty()).orElseThrow();

    assertThat(result).isInstanceOf(MarkdownType.class);
    assertThat(result.getValue()).isEqualTo("PSEUDONYMISIERT");
  }

  /** A name part without a value has nothing to replace; replacing it would invent one. */
  @Test
  void stringReplacementHandlerPassesAValuelessStringThrough() {
    DeidentifierHandler<StringType> handler = Handlers.stringReplacementHandler("PSEUDONYMISIERT");
    StringType valueless = new StringType();

    assertThat(handler.apply(valueless, HandlerContext.empty())).containsSame(valueless);
  }

  /**
   * A date element can carry only extensions, e.g. a data-absent-reason, and no value. The handler
   * returns it unchanged, so the engine's extension whitelisting still runs.
   */
  @Test
  void generalizeDatePassesAValuelessDateThrough() {
    DateType valueless = new DateType();

    assertThat(Handlers.generalizeDateHandler(valueless, HandlerContext.empty()).orElseThrow())
        .isSameAs(valueless);
  }
}
