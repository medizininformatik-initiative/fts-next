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
import org.junit.jupiter.params.provider.ValueSource;

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

  private static final IDReplacementProvider NO_ID_LOOKUP =
      (type, id) -> {
        throw new AssertionError("the id provider must not be called");
      };

  private static final IdentifierValueReplacementProvider NO_VALUE_LOOKUP =
      (system, value) -> {
        throw new AssertionError("the identifier provider must not be called");
      };

  @Test
  void referenceReplacementHandlerRejectsAMissingProvider() {
    assertThatThrownBy(() -> Handlers.referenceReplacementHandler(null, NO_VALUE_LOOKUP))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> Handlers.referenceReplacementHandler(NO_ID_LOOKUP, null))
        .isInstanceOf(NullPointerException.class);
  }

  /**
   * The type for the lookup is the type the reference names, not the type of the resource that
   * holds the reference.
   */
  @Test
  void referenceReplacementHandlerReplacesTheIdOfARelativeReferenceUnderTheReferencedType() {
    var calls = new ArrayList<List<String>>();
    var handler =
        Handlers.referenceReplacementHandler(
            (type, id) -> {
              calls.add(List.of(type, id));
              return "pseudonym";
            },
            NO_VALUE_LOOKUP);
    var context = HandlerContext.of(List.of(new Patient()), "p");

    var result = handler.apply(new StringType("Organization/123"), context).orElseThrow();

    assertThat(calls).containsExactly(List.of("Organization", "123"));
    assertThat(result.getValue()).isEqualTo("Organization/pseudonym");
  }

  /**
   * A reference can carry only extensions, e.g. a data-absent-reason. The handler returns it
   * unchanged and asks no provider for a pseudonym of nothing.
   */
  @Test
  void referenceReplacementHandlerPassesAValuelessReferenceThrough() {
    var handler = Handlers.referenceReplacementHandler(NO_ID_LOOKUP, NO_VALUE_LOOKUP);
    var valueless = new StringType();

    assertThat(handler.apply(valueless, CONTEXT)).containsSame(valueless);
  }

  /** A contained reference names an element of the same resource, not a source id. */
  @Test
  void referenceReplacementHandlerKeepsAContainedReference() {
    var handler = Handlers.referenceReplacementHandler(NO_ID_LOOKUP, NO_VALUE_LOOKUP);
    var contained = new StringType("#med1");

    assertThat(handler.apply(contained, CONTEXT)).containsSame(contained);
  }

  /** The version names a state of the resource the source chose, so it does not survive. */
  @Test
  void referenceReplacementHandlerDropsTheVersionOfAVersionedReference() {
    var calls = new ArrayList<List<String>>();
    var handler =
        Handlers.referenceReplacementHandler(
            (type, id) -> {
              calls.add(List.of(type, id));
              return "pseudonym";
            },
            NO_VALUE_LOOKUP);

    var result = handler.apply(new StringType("Patient/123/_history/7"), CONTEXT).orElseThrow();

    assertThat(calls).containsExactly(List.of("Patient", "123"));
    assertThat(result.getValue()).isEqualTo("Patient/pseudonym");
  }

  /**
   * A conditional reference names the resource by one identifier. A missing system and an empty one
   * ("explicitly no system") both look up under the placeholder, and the output keeps the shape of
   * the input. The value is everything after the first bar.
   */
  @ParameterizedTest(name = "{0} -> {3}")
  @CsvSource(
      delimiter = ';',
      value = {
        "Patient?identifier=http://sys|123; http://sys; 123; Patient?identifier=http://sys|PSEUDO",
        "Patient?identifier=123; <no_system>; 123; Patient?identifier=PSEUDO",
        "Patient?identifier=|123; <no_system>; 123; Patient?identifier=|PSEUDO",
        "Patient?identifier=http://sys|a|b; http://sys; a|b; Patient?identifier=http://sys|PSEUDO",
        "Patient?identifier=|a|b; <no_system>; a|b; Patient?identifier=|PSEUDO",
        "Patient?identifier=http%3A%2F%2Fsys%7C123; http://sys; 123;"
            + " Patient?identifier=http%3A%2F%2Fsys%7CPSEUDO",
        "Patient?identifier=http%3A%2F%2Fsys%7c123; http://sys; 123;"
            + " Patient?identifier=http%3A%2F%2Fsys%7cPSEUDO",
        "Patient?identifier=%7C123; <no_system>; 123; Patient?identifier=%7CPSEUDO",
        "Patient?identifier=sys|a%7Cb; sys; a|b; Patient?identifier=sys|PSEUDO",
        "Patient?identifier=a%20b; <no_system>; a b; Patient?identifier=PSEUDO",
        "Patient?identifier=sys|a+b; sys; a+b; Patient?identifier=sys|PSEUDO",
        "Patient?identifier=sys|%C3%A4; sys; ä; Patient?identifier=sys|PSEUDO"
      })
  void referenceReplacementHandlerReplacesTheValueOfAConditionalReference(
      String reference, String system, String value, String expected) {
    var calls = new ArrayList<List<String>>();
    var handler =
        Handlers.referenceReplacementHandler(
            NO_ID_LOOKUP,
            (lookedUpSystem, lookedUpValue) -> {
              calls.add(List.of(lookedUpSystem, lookedUpValue));
              return "PSEUDO";
            });

    var result = handler.apply(new StringType(reference), CONTEXT).orElseThrow();

    assertThat(calls).containsExactly(List.of(system, value));
    assertThat(result.getValue()).isEqualTo(expected);
  }

  /** The message must not carry the urn: source data must not reach the logs. */
  @Test
  void referenceReplacementHandlerRejectsAUrnUuidReferenceWithoutLeakingIt() {
    var handler = Handlers.referenceReplacementHandler(NO_ID_LOOKUP, NO_VALUE_LOOKUP);
    var reference = new StringType("urn:uuid:5f2d5a6c-0000-4000-8000-000000000001");

    assertThatThrownBy(() -> handler.apply(reference, CONTEXT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("A urn:uuid reference cannot be resolved.")
        .hasMessageNotContaining("5f2d5a6c");
  }

  /** The message must not carry the reference: source data must not reach the logs. */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://server/fhir/Patient/secret-1",
        "https://server/fhir/Patient/secret-1/_history/2",
        "HTTP://server/fhir/Patient?identifier=secret-1",
        "x+y.z-1://secret-1"
      })
  void referenceReplacementHandlerRejectsAnAbsoluteReferenceWithoutLeakingIt(String reference) {
    var handler = Handlers.referenceReplacementHandler(NO_ID_LOOKUP, NO_VALUE_LOOKUP);

    assertThatThrownBy(() -> handler.apply(new StringType(reference), CONTEXT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("An absolute reference cannot be pseudonymized.")
        .hasMessageNotContaining("secret-1");
  }

  /**
   * Anything that is neither a type and id nor a conditional on the identifier alone names no
   * resource the handler can replace. A conditional with an empty value would look up a pseudonym
   * for nothing.
   */
  @ParameterizedTest
  @ValueSource(
      strings = {
        "Patient?name=secret-1",
        "Patient?identifier=secret-1&name=secret-2",
        "Patient?identifier=",
        "Patient?identifier=http://sys|",
        "Patient?identifier=|",
        "Patient?identifier=sys%7C",
        "Patient?identifier=sys%7c",
        "Patient?identifier=%7C",
        "Patient?identifier=secret-1%ZZ",
        "Patient?identifier=secret-1%7",
        "Patient?identifier=secret-1%ZZ%7C1",
        "Patient?identifier=sys|secret-1%",
        "Patient?identifier=sys|secret-1%G1",
        "Patient?identifier=sys|secret-1%1G",
        "Patient?identifier=sys|secret-1%FF",
        "Patient?identifier=sys|secret-1%C3",
        "Patient?identifier=sys|secret-1%C3x",
        "Patient?identifier=sys|secret-1%C0%AF",
        "Patient?identifier=secret-1%FF|1",
        "Patient?identifier=secret-1%C3%7C1",
        "Patient?identifier=secret-1%C0%AF|1",
        "Patient",
        "secret-1/b/c",
        "Patient/secret-1/_history",
        "Patient/secret-1?_format=json",
        "urn:oid:1.2.3.secret-1",
        ""
      })
  void referenceReplacementHandlerRejectsAnyOtherFormatWithoutLeakingIt(String reference) {
    var handler = Handlers.referenceReplacementHandler(NO_ID_LOOKUP, NO_VALUE_LOOKUP);

    assertThatThrownBy(() -> handler.apply(new StringType(reference), CONTEXT))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("A reference has a format that cannot be pseudonymized.")
        .hasMessageNotContaining("secret-1")
        .hasMessageNotContaining("secret-2");
  }

  /** The pseudonym is encoded so it cannot end the token; a space is %20, as + is a plus sign. */
  @Test
  void referenceReplacementHandlerPercentEncodesThePseudonymOfAConditionalReference() {
    var handler =
        Handlers.referenceReplacementHandler(NO_ID_LOOKUP, (system, value) -> "a b&c|d+\u00e9");

    var result = handler.apply(new StringType("Patient?identifier=sys|1"), CONTEXT).orElseThrow();

    assertThat(result.getValue()).isEqualTo("Patient?identifier=sys|a%20b%26c%7Cd%2B%C3%A9");
  }
}
