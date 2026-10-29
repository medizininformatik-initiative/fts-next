package care.smith.fts.deidentifier.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import care.smith.fts.deidentifier.DeidentifierHandler;
import care.smith.fts.deidentifier.HandlerContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MarkdownType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class HandlersTest {

  private static final HandlerContext CONTEXT = HandlerContext.of(List.of(), "p");

  /** A five-digit code keeps its first three digits; any other code is removed. */
  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({"48149, 481", "1234, ", ", "})
  void generalizePostalCodeKeepsThreeOfFiveDigits(String code, String expected) {
    assertThat(
            Handlers.generalizePostalCode(new StringType(code), CONTEXT).map(StringType::getValue))
        .isEqualTo(Optional.ofNullable(expected));
  }

  /** A date with a day gets day 15; a coarser date stays unchanged. */
  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource({"1970-05-12, 1970-05-15", "1970, 1970", "1970-05, 1970-05"})
  void generalizeDateSetsTheDayOfMonthToTheFifteenth(String date, String expected) {
    DateType result = Handlers.generalizeDateHandler(new DateType(date), CONTEXT).orElseThrow();

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

    assertThatThrownBy(() -> Handlers.generalizeDateHandler(date, CONTEXT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unexpected precision for object of type DateType!");
  }

  @Test
  void stringReplacementHandlerReplacesTheValueWithTheConfiguredString() {
    DeidentifierHandler<StringType> handler = Handlers.stringReplacementHandler("PSEUDONYMISIERT");

    StringType result = handler.apply(new StringType("Doe"), CONTEXT).orElseThrow();

    assertThat(result.getValue()).isEqualTo("PSEUDONYMISIERT");
  }

  /**
   * Annotation.text is a MarkdownType, a subtype of StringType. The result is written back into
   * that field, so it has to keep the class of the element it replaces.
   */
  @Test
  void stringReplacementHandlerKeepsTheClassOfTheElement() {
    DeidentifierHandler<StringType> handler = Handlers.stringReplacementHandler("PSEUDONYMISIERT");

    StringType result = handler.apply(new MarkdownType("free text"), CONTEXT).orElseThrow();

    assertThat(result).isInstanceOf(MarkdownType.class);
    assertThat(result.getValue()).isEqualTo("PSEUDONYMISIERT");
  }

  /** A name part without a value has nothing to replace; replacing it would invent one. */
  @Test
  void stringReplacementHandlerPassesAValuelessStringThrough() {
    DeidentifierHandler<StringType> handler = Handlers.stringReplacementHandler("PSEUDONYMISIERT");
    StringType valueless = new StringType();

    assertThat(handler.apply(valueless, CONTEXT)).containsSame(valueless);
  }

  /**
   * A date element can carry only extensions, e.g. a data-absent-reason, and no value. The handler
   * returns it unchanged, so the engine's extension whitelisting still runs.
   */
  @Test
  void generalizeDatePassesAValuelessDateThrough() {
    DateType valueless = new DateType();

    assertThat(Handlers.generalizeDateHandler(valueless, CONTEXT).orElseThrow())
        .isSameAs(valueless);
  }

  @Test
  void idReplacementHandlerRejectsAMissingProvider() {
    assertThatThrownBy(() -> Handlers.idReplacementHandler(null))
        .isInstanceOf(NullPointerException.class);
  }

  /**
   * The resource type for the lookup is the type of the resource the id belongs to. The IdType may
   * carry another type, or none.
   */
  @Test
  void idReplacementHandlerReplacesTheIdPartUnderTheTypeOfTheResource() {
    var calls = new ArrayList<List<String>>();
    var handler =
        Handlers.idReplacementHandler(
            (resourceType, id) -> {
              calls.add(List.of(resourceType, id));
              return "pseudonym";
            });
    var resource = new Patient();
    var context = HandlerContext.of(List.of(resource), "p");

    var result = handler.apply(new IdType("Observation", "123"), context).orElseThrow();

    assertThat(calls).containsExactly(List.of("Patient", "123"));
    assertThat(result.getValue()).isEqualTo("Observation/pseudonym");
  }

  /**
   * HAPI keeps the fullUrl urn as the id of a transaction-bundle resource. The provider sees the
   * bare uuid, so a resource keeps its pseudonym whether it is read with or without the urn.
   */
  @Test
  void idReplacementHandlerLooksUpTheUuidOfAUrnId() {
    var ids = new ArrayList<String>();
    var handler =
        Handlers.idReplacementHandler(
            (resourceType, id) -> {
              ids.add(id);
              return "pseudonym";
            });
    var context = HandlerContext.of(List.of(new Patient()), "p");

    handler.apply(new IdType("urn:uuid:5f2d5a6c-0000-4000-8000-000000000001"), context);

    assertThat(ids).containsExactly("5f2d5a6c-0000-4000-8000-000000000001");
  }

  /** An id element can carry only extensions and no id part; there is nothing to look up. */
  @Test
  void idReplacementHandlerPassesAnIdWithoutIdPartThrough() {
    var handler =
        Handlers.idReplacementHandler(
            (resourceType, id) -> {
              throw new AssertionError("the provider must not be called");
            });
    var valueless = new IdType();

    assertThat(handler.apply(valueless, HandlerContext.of(List.of(new Patient()), "p")))
        .containsSame(valueless);
  }

  @Test
  void identifierValueReplacementHandlerRejectsAMissingProvider() {
    assertThatThrownBy(() -> Handlers.identifierValueReplacementHandler(null, true))
        .isInstanceOf(NullPointerException.class);
  }

  /** The system comes from the enclosing identifier, which is the parent of the value. */
  @Test
  void identifierValueReplacementHandlerReplacesTheValueWithinTheSystemOfItsIdentifier() {
    var handler =
        Handlers.identifierValueReplacementHandler(
            (system, value) -> "%s|%s|pseudonym".formatted(system, value), false);
    var identifier = new Identifier().setSystem("http://sys").setValue("123");
    var context = HandlerContext.of(List.of(new Patient(), identifier), "p");

    var result = handler.apply(identifier.getValueElement(), context).orElseThrow();

    assertThat(result.getValue()).isEqualTo("http://sys|123|pseudonym");
  }

  /**
   * A value can carry only extensions, e.g. a data-absent-reason. The handler returns it unchanged
   * and does not ask the provider for a pseudonym of nothing.
   */
  @Test
  void identifierValueReplacementHandlerPassesAValuelessValueThrough() {
    var handler =
        Handlers.identifierValueReplacementHandler(
            (system, value) -> {
              throw new AssertionError("the provider must not be called");
            },
            false);
    var valueless = new StringType();
    var context =
        HandlerContext.of(List.of(new Patient(), new Identifier().setSystem("http://sys")), "p");

    assertThat(handler.apply(valueless, context)).containsSame(valueless);
  }

  /** Without a system, the value is replaced under a placeholder system, if the caller accepts. */
  @Test
  void identifierValueReplacementHandlerUsesAPlaceholderSystemWhenAcceptingNoSystem() {
    var handler =
        Handlers.identifierValueReplacementHandler(
            (system, value) -> "%s|%s|pseudonym".formatted(system, value), true);
    var identifier = new Identifier().setValue("123");
    var context = HandlerContext.of(List.of(new Patient(), identifier), "p");

    var result = handler.apply(identifier.getValueElement(), context).orElseThrow();

    assertThat(Handlers.NO_SYSTEM).isEqualTo("<no_system>");
    assertThat(result.getValue()).isEqualTo("<no_system>|123|pseudonym");
  }

  /** The message must not carry the value: source data must not reach the logs. */
  @Test
  void identifierValueReplacementHandlerRejectsAMissingSystemWithoutLeakingTheValue() {
    var handler = Handlers.identifierValueReplacementHandler((system, value) -> "x", false);
    var identifier = new Identifier().setValue("secret-123");
    var context = HandlerContext.of(List.of(new Patient(), identifier), "p");

    assertThatThrownBy(() -> handler.apply(identifier.getValueElement(), context))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("An identifier is missing a system, which this replacement requires.")
        .hasMessageNotContaining("secret-123");
  }
}
