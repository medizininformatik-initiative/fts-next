package care.smith.fts.deidentifhir.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import care.smith.fts.deidentifhir.DeidentifhirHandler;
import care.smith.fts.deidentifhir.HandlerContext;
import java.util.List;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Identifier;
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
   * The provider is asked for the bare uuid under the {@code urn:uuid} key, and its answer is
   * shaped into a UUID, because {@code urn:uuid:<anything>} is not a valid URI for the HL7
   * validator. The expected value is the shaping of the pseudonym {@code SYNTH-1}, see {@link
   * #urnPseudonymIsTheShapedProviderAnswer()}.
   */
  @Test
  void referenceReplacementHandlerShapesTheProviderAnswerOfAUrnReferenceIntoAUuid() {
    DeidentifhirHandler<StringType> handler =
        Handlers.referenceReplacementHandler(
            (resourceType, id) ->
                resourceType.equals("urn:uuid") && id.equals("8d1f-42") ? "SYNTH-1" : "wrong-key",
            (system, value) -> "value-pseudonym");

    StringType result =
        handler
            .apply(NO_PATH, new StringType("urn:uuid:8d1f-42"), HandlerContext.empty())
            .orElseThrow();

    assertThat(result.getValue()).isEqualTo("urn:uuid:79224f4a-b34f-478d-be55-50c9cf9d4f37");
  }

  /**
   * Pins the shaping itself: the UUID is the 128-bit FNV-1a hash of the pseudonym with the version
   * nibble stamped to {@code 4} and the variant nibble taken from the two lowest hash bits. The
   * expected values were produced by an independent implementation of that algorithm, run on {@code
   * SYNTH-1} and {@code other}.
   */
  @Test
  void urnPseudonymIsTheShapedProviderAnswer() {
    DeidentifhirHandler<StringType> handler =
        Handlers.referenceReplacementHandler(
            (resourceType, id) -> id.equals("a") ? "SYNTH-1" : "id-pseudonym",
            (system, value) -> "value-pseudonym");

    assertThat(
            handler
                .apply(NO_PATH, new StringType("urn:uuid:a"), HandlerContext.empty())
                .orElseThrow()
                .getValue())
        .isEqualTo("urn:uuid:79224f4a-b34f-478d-be55-50c9cf9d4f37");
    assertThat(
            handler
                .apply(NO_PATH, new StringType("urn:uuid:b"), HandlerContext.empty())
                .orElseThrow()
                .getValue())
        .isEqualTo("urn:uuid:52a47402-9e1f-438b-b31c-fad31f14ca93");
  }

  /**
   * The link a transaction bundle resolves: a {@code urn:uuid:} reference and the {@code fullUrl}
   * of the entry it points at both derive from the pseudonym of the same id, so both sides of the
   * link carry the same value while the resource keeps the plain pseudonym as its id.
   */
  @Test
  void referenceReplacementHandlerDerivesAUrnReferenceFromTheSamePseudonymAsTheEntryId() {
    IDReplacementProvider provider = (resourceType, id) -> "pseudonym-of-" + id;
    Patient entry = new Patient();
    entry.setId("urn:uuid:8d1f-42");

    IdType id =
        Handlers.idReplacementHandler(provider)
            .apply(NO_PATH, entry.getIdElement(), HandlerContext.of(List.of(entry)))
            .orElseThrow();
    StringType reference =
        Handlers.referenceReplacementHandler(provider, (system, value) -> "value-pseudonym")
            .apply(NO_PATH, new StringType("urn:uuid:8d1f-42"), HandlerContext.empty())
            .orElseThrow();

    assertThat(id.getIdPart()).isEqualTo("pseudonym-of-8d1f-42");
    assertThat(reference.getValue()).isEqualTo("urn:uuid:a9fb0f8c-30e2-43ed-8372-6e689ea717d8");
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
        .hasMessageContaining("absolute");
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
  void referenceReplacementHandlerRewritesUrnReferences() {
    DeidentifhirHandler<StringType> handler =
        Handlers.referenceReplacementHandler(
            (resourceType, id) -> "id-pseudonym", (system, value) -> "value-pseudonym");

    StringType result =
        handler
            .apply(NO_PATH, new StringType("urn:uuid:8d1f-42"), HandlerContext.empty())
            .orElseThrow();

    assertThat(result.getValue()).isEqualTo("urn:uuid:52a47402-9e1f-438b-b31c-fad31f14ca93");
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

  @Test
  void referenceReplacementHandlerRejectsAReferenceWithoutAValue() {
    DeidentifhirHandler<StringType> handler =
        Handlers.referenceReplacementHandler(
            (resourceType, id) -> "pseudonym", (system, value) -> "value-pseudonym");

    assertThatThrownBy(() -> handler.apply(NO_PATH, new StringType(), HandlerContext.empty()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("without a value");
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
