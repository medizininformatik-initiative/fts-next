package care.smith.fts.deidentifhir.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import care.smith.fts.deidentifhir.DeidentifhirHandler;
import care.smith.fts.deidentifhir.HandlerContext;
import java.util.List;
import java.util.Map;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.MarkdownType;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;

class HandlersTest {

  private static final List<String> NO_PATH = List.of();

  @Test
  void generalizePostalCodeTruncatesFiveDigitCodeToThreeDigits() {
    StringType result =
        Handlers.generalizePostalCode(NO_PATH, new StringType("48149"), HandlerContext.empty())
            .orElseThrow();

    assertThat(result.getValue()).isEqualTo("481");
  }

  @Test
  void generalizePostalCodeRemovesCodesThatAreNotFiveDigitsLong() {
    assertThat(
            Handlers.generalizePostalCode(NO_PATH, new StringType("1234"), HandlerContext.empty()))
        .isEmpty();
    assertThat(Handlers.generalizePostalCode(NO_PATH, new StringType(), HandlerContext.empty()))
        .isEmpty();
  }

  @Test
  void generalizeDateSetsTheDayOfMonthToTheFifteenth() {
    DateType result =
        Handlers.generalizeDateHandler(NO_PATH, new DateType("1970-05-12"), HandlerContext.empty())
            .orElseThrow();

    assertThat(result.getValueAsString()).isEqualTo("1970-05-15");
  }

  @Test
  void generalizeDateLeavesDatesCoarserThanDayUntouched() {
    assertThat(
            Handlers.generalizeDateHandler(NO_PATH, new DateType("1970"), HandlerContext.empty())
                .orElseThrow()
                .getValueAsString())
        .isEqualTo("1970");
    assertThat(
            Handlers.generalizeDateHandler(NO_PATH, new DateType("1970-05"), HandlerContext.empty())
                .orElseThrow()
                .getValueAsString())
        .isEqualTo("1970-05");
  }

  @Test
  void stringReplacementHandlerReplacesTheValueWithTheConfiguredString() {
    DeidentifhirHandler<StringType> handler = Handlers.stringReplacementHandler("PSEUDONYMISIERT");

    StringType result =
        handler.apply(NO_PATH, new StringType("Doe"), HandlerContext.empty()).orElseThrow();

    assertThat(result.getValue()).isEqualTo("PSEUDONYMISIERT");
  }

  /**
   * Annotation.text is a MarkdownType, a subtype of StringType. The result is written back into
   * that field, so it has to keep the class of the element it replaces.
   */
  @Test
  void stringReplacementHandlerKeepsTheClassOfTheElement() {
    DeidentifhirHandler<StringType> handler = Handlers.stringReplacementHandler("PSEUDONYMISIERT");

    StringType result =
        handler.apply(NO_PATH, new MarkdownType("free text"), HandlerContext.empty()).orElseThrow();

    assertThat(result).isInstanceOf(MarkdownType.class);
    assertThat(result.getValue()).isEqualTo("PSEUDONYMISIERT");
  }

  /** A name part without a value has nothing to replace; replacing it would invent one. */
  @Test
  void stringReplacementHandlerPassesAValuelessStringThrough() {
    DeidentifhirHandler<StringType> handler = Handlers.stringReplacementHandler("PSEUDONYMISIERT");
    StringType valueless = new StringType();

    assertThat(handler.apply(NO_PATH, valueless, HandlerContext.empty())).containsSame(valueless);
  }

  /**
   * A date element can carry only extensions, e.g. a data-absent-reason, and no value. The handler
   * returns it unchanged, so the engine's extension whitelisting still runs.
   */
  @Test
  void generalizeDatePassesAValuelessDateThrough() {
    DateType valueless = new DateType();

    assertThat(
            Handlers.generalizeDateHandler(NO_PATH, valueless, HandlerContext.empty())
                .orElseThrow())
        .isSameAs(valueless);
  }

  @Test
  void referenceReplacementHandlerReplacesTheIdPartOfARelativeReference() {
    DeidentifhirHandler<StringType> handler =
        Handlers.referenceReplacementHandler(
            (resourceType, id) -> "pseudonym-of-" + resourceType + "-" + id,
            (system, value) -> "value-pseudonym");

    StringType result =
        handler.apply(NO_PATH, new StringType("Patient/123"), HandlerContext.empty()).orElseThrow();

    assertThat(result.getValue()).isEqualTo("Patient/pseudonym-of-Patient-123");
  }

  /**
   * A urn reference names no resource type; the handler takes it from the bundle entry whose
   * fullUrl the urn is, and asks for the bare uuid under that type.
   */
  @Test
  void referenceReplacementHandlerResolvesAUrnReferenceThroughTheEntryItNames() {
    DeidentifhirHandler<StringType> handler =
        Handlers.referenceReplacementHandler(
            (resourceType, id) ->
                resourceType.equals("Patient") && id.equals("8d1f-42") ? "SYNTH-1" : "wrong-key",
            (system, value) -> "value-pseudonym");
    HandlerContext context =
        HandlerContext.empty().withEntryTypes(Map.of("urn:uuid:8d1f-42", "Patient"));

    StringType result =
        handler.apply(NO_PATH, new StringType("urn:uuid:8d1f-42"), context).orElseThrow();

    assertThat(result.getValue()).isEqualTo("Patient/SYNTH-1");
  }

  /** A urn that no entry of the bundle carries points at nothing, so the reference is removed. */
  @Test
  void referenceReplacementHandlerRemovesAUrnReferenceThatNoEntryCarries() {
    DeidentifhirHandler<StringType> handler =
        Handlers.referenceReplacementHandler(
            (resourceType, id) -> "pseudonym", (system, value) -> "value-pseudonym");

    assertThat(handler.apply(NO_PATH, new StringType("urn:uuid:8d1f-42"), HandlerContext.empty()))
        .isEmpty();
  }

  /**
   * Both ends of a urn link are keyed alike: the id of the entry, read by HAPI from its urn
   * fullUrl, and a reference to it take the pseudonym of the same type and uuid.
   */
  @Test
  void referenceReplacementHandlerGivesAUrnReferenceThePseudonymOfTheEntryId() {
    IDReplacementProvider provider =
        (resourceType, id) -> "pseudonym-of-" + resourceType + "-" + id;
    Patient entry = new Patient();
    entry.setId("urn:uuid:8d1f-42");

    IdType id =
        Handlers.idReplacementHandler(provider)
            .apply(NO_PATH, entry.getIdElement(), HandlerContext.of(List.of(entry)))
            .orElseThrow();
    StringType reference =
        Handlers.referenceReplacementHandler(provider, (system, value) -> "value-pseudonym")
            .apply(
                NO_PATH,
                new StringType("urn:uuid:8d1f-42"),
                HandlerContext.empty().withEntryTypes(Map.of("urn:uuid:8d1f-42", "Patient")))
            .orElseThrow();

    assertThat(reference.getValue()).isEqualTo("Patient/" + id.getIdPart());
  }

  /** The exact reference shape of the MII corpus: the system of the search URI carries slashes. */
  @Test
  void referenceReplacementHandlerRewritesAConditionalReferenceWithASystem() {
    DeidentifhirHandler<StringType> handler =
        Handlers.referenceReplacementHandler(
            (resourceType, id) -> "id-pseudonym",
            (system, value) -> "value-pseudonym-of-" + system + "-" + value);

    StringType result =
        handler
            .apply(
                NO_PATH,
                new StringType(
                    "Location?identifier=https://github.com/synthetichealth/synthea|1fe64421-e135"),
                HandlerContext.empty())
            .orElseThrow();

    assertThat(result.getValue())
        .isEqualTo(
            "Location?identifier=https://github.com/synthetichealth/synthea"
                + "|value-pseudonym-of-https://github.com/synthetichealth/synthea-1fe64421-e135");
  }

  @Test
  void referenceReplacementHandlerRewritesAConditionalReferenceWithoutASystem() {
    DeidentifhirHandler<StringType> handler =
        Handlers.referenceReplacementHandler(
            (resourceType, id) -> "id-pseudonym",
            (system, value) -> "value-pseudonym-of-" + system + "-" + value);

    StringType result =
        handler
            .apply(NO_PATH, new StringType("Patient?identifier=12345"), HandlerContext.empty())
            .orElseThrow();

    assertThat(result.getValue())
        .isEqualTo("Patient?identifier=value-pseudonym-of-<no_system>-12345");
  }

  @Test
  void referenceReplacementHandlerRejectsAbsoluteReferencesInsteadOfMisreadingThem() {
    DeidentifhirHandler<StringType> handler =
        Handlers.referenceReplacementHandler(
            (resourceType, id) -> "pseudonym", (system, value) -> "value-pseudonym");

    assertThatThrownBy(
            () ->
                handler
                    .apply(
                        NO_PATH,
                        new StringType("https://server.example/fhir/Patient/123"),
                        HandlerContext.empty())
                    .orElseThrow())
        .hasMessageContaining("absolute")
        // the message reaches the logs and the failed-patients API; it must not carry source data
        .hasMessageNotContaining("server.example")
        .hasMessageNotContaining("123");
  }

  @Test
  void referenceReplacementHandlerRejectsReferencesThatAreNotRelative() {
    DeidentifhirHandler<StringType> handler =
        Handlers.referenceReplacementHandler(
            (resourceType, id) -> "pseudonym", (system, value) -> "value-pseudonym");

    assertThatThrownBy(
            () ->
                handler.apply(NO_PATH, new StringType("123"), HandlerContext.empty()).orElseThrow())
        .hasMessageContaining("only relative references are supported");
    assertThatThrownBy(
            () ->
                handler
                    .apply(NO_PATH, new StringType("urn:oid:1.2.3"), HandlerContext.empty())
                    .orElseThrow())
        .hasMessageContaining("only relative references are supported");
  }

  /** Without a resource type the id would be pseudonymized under an empty type, "/pseudonym". */
  @Test
  void referenceReplacementHandlerRejectsAReferenceWithoutResourceType() {
    DeidentifhirHandler<StringType> handler =
        Handlers.referenceReplacementHandler(
            (resourceType, id) -> "pseudonym", (system, value) -> "value-pseudonym");

    assertThatThrownBy(
            () ->
                handler
                    .apply(NO_PATH, new StringType("/123"), HandlerContext.empty())
                    .orElseThrow())
        .hasMessageContaining("only relative references are supported");
  }

  @Test
  void referenceReplacementHandlerRewritesSearchReferences() {
    DeidentifhirHandler<StringType> handler =
        Handlers.referenceReplacementHandler(
            (resourceType, id) -> "id-pseudonym",
            (system, value) -> "value-pseudonym-of-" + system + "-" + value);

    StringType result =
        handler
            .apply(
                NO_PATH,
                new StringType("Patient?identifier=mySystem|12345"),
                HandlerContext.empty())
            .orElseThrow();

    assertThat(result.getValue())
        .isEqualTo("Patient?identifier=mySystem|value-pseudonym-of-mySystem-12345");
  }

  @Test
  void referenceReplacementHandlerRewritesRelativeReferences() {
    DeidentifhirHandler<StringType> handler =
        Handlers.referenceReplacementHandler(
            (resourceType, id) -> "id-pseudonym", (system, value) -> "value-pseudonym");

    StringType result =
        handler.apply(NO_PATH, new StringType("Patient/123"), HandlerContext.empty()).orElseThrow();

    assertThat(result.getValue()).isEqualTo("Patient/id-pseudonym");
  }

  /**
   * A versioned reference names the same resource as its unversioned form, so it takes the same
   * pseudonym. The version belongs to the source system and does not exist in the target.
   */
  @Test
  void referenceReplacementHandlerPseudonymizesAVersionedReferenceUnderItsIdAndDropsTheVersion() {
    DeidentifhirHandler<StringType> handler =
        Handlers.referenceReplacementHandler(
            (resourceType, id) -> "pseudonym-of-" + resourceType + "-" + id,
            (system, value) -> "value-pseudonym");

    StringType result =
        handler
            .apply(NO_PATH, new StringType("Condition/c1/_history/2"), HandlerContext.empty())
            .orElseThrow();

    assertThat(result.getValue()).isEqualTo("Condition/pseudonym-of-Condition-c1");
  }

  /** A contained reference points inside its own resource and carries no id of the source. */
  @Test
  void referenceReplacementHandlerKeepsAContainedReference() {
    DeidentifhirHandler<StringType> handler =
        Handlers.referenceReplacementHandler(
            (resourceType, id) -> "pseudonym", (system, value) -> "value-pseudonym");

    StringType result =
        handler.apply(NO_PATH, new StringType("#med1"), HandlerContext.empty()).orElseThrow();

    assertThat(result.getValue()).isEqualTo("#med1");
  }

  @Test
  void idReplacementHandlerTakesTheResourceTypeFromTheContextRoot() {
    DeidentifhirHandler<IdType> handler =
        Handlers.idReplacementHandler(
            (resourceType, id) -> "pseudonym-of-" + resourceType + "-" + id);
    Patient patient = new Patient();
    patient.setId("123");

    IdType result =
        handler
            .apply(NO_PATH, patient.getIdElement(), HandlerContext.of(List.of(patient)))
            .orElseThrow();

    assertThat(result.getIdPart()).isEqualTo("pseudonym-of-Patient-123");
  }

  @Test
  void idReplacementHandlerStripsTheUrnUuidPrefixHapiReadsFromTheEntry() {
    DeidentifhirHandler<IdType> handler =
        Handlers.idReplacementHandler((resourceType, id) -> "pseudonym-of-" + id);
    Patient patient = new Patient();
    patient.setId("urn:uuid:8d1f-42");

    IdType result =
        handler
            .apply(NO_PATH, patient.getIdElement(), HandlerContext.of(List.of(patient)))
            .orElseThrow();

    assertThat(result.getIdPart()).isEqualTo("pseudonym-of-8d1f-42");
  }

  /** A reference can carry only a data-absent-reason; there is no id part to pseudonymize. */
  @Test
  void referenceReplacementHandlerPassesAValuelessReferenceThrough() {
    DeidentifhirHandler<StringType> handler =
        Handlers.referenceReplacementHandler(
            (resourceType, id) -> "pseudonym", (system, value) -> "value-pseudonym");
    StringType valueless = new StringType();

    assertThat(handler.apply(NO_PATH, valueless, HandlerContext.empty())).containsSame(valueless);
  }

  /** An id element can carry only extensions; there is no id part to pseudonymize. */
  @Test
  void idReplacementHandlerPassesAValuelessIdThrough() {
    DeidentifhirHandler<IdType> handler =
        Handlers.idReplacementHandler((resourceType, id) -> "pseudonym");
    IdType valueless = new IdType();

    assertThat(handler.apply(NO_PATH, valueless, HandlerContext.empty())).containsSame(valueless);
  }

  @Test
  void identifierValueReplacementHandlerPrefixesTheValueWithTheIdentifiersSystem() {
    DeidentifhirHandler<StringType> handler =
        Handlers.identifierValueReplacementHandler(
            (system, value) -> "pseudonym-of-" + system + "-" + value, false);
    Patient patient = new Patient();
    Identifier identifier = patient.addIdentifier().setSystem("mySystem").setValue("12345");

    StringType result =
        handler
            .apply(
                NO_PATH,
                identifier.getValueElement(),
                HandlerContext.of(List.of(patient, identifier)))
            .orElseThrow();

    assertThat(result.getValue()).isEqualTo("pseudonym-of-mySystem-12345");
  }

  /**
   * A masked identifier carries only a data-absent-reason. Pseudonymizing its missing value would
   * invent an identifier and map every masked identifier of one system to the same pseudonym.
   */
  @Test
  void identifierValueReplacementHandlerPassesAValuelessValueThrough() {
    DeidentifhirHandler<StringType> handler =
        Handlers.identifierValueReplacementHandler((system, value) -> "pseudonym", true);
    Identifier identifier = new Identifier().setSystem("mySystem");
    StringType valueless = identifier.getValueElement();

    assertThat(handler.apply(NO_PATH, valueless, HandlerContext.of(List.of(identifier))))
        .containsSame(valueless);
  }

  @Test
  void identifierValueReplacementHandlerFallsBackToAPlaceholderSystemWhenThatIsAccepted() {
    DeidentifhirHandler<StringType> handler =
        Handlers.identifierValueReplacementHandler((system, value) -> system + "-" + value, true);
    Identifier identifier = new Identifier().setValue("12345");

    StringType result =
        handler
            .apply(NO_PATH, identifier.getValueElement(), HandlerContext.of(List.of(identifier)))
            .orElseThrow();

    assertThat(result.getValue()).isEqualTo("<no_system>-12345");
  }

  @Test
  void identifierValueReplacementHandlerRejectsIdentifiersWithoutASystemByDefault() {
    DeidentifhirHandler<StringType> handler =
        Handlers.identifierValueReplacementHandler((system, value) -> "pseudonym", false);
    Identifier identifier = new Identifier().setValue("12345");

    assertThatThrownBy(
            () ->
                handler
                    .apply(
                        NO_PATH,
                        identifier.getValueElement(),
                        HandlerContext.of(List.of(identifier)))
                    .orElseThrow())
        .hasMessageContaining("is missing a system");
  }
}
