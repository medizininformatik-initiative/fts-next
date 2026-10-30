package care.smith.fts.deidentifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import care.smith.fts.deidentifier.internal.PseudonymUuid;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Binary;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class BundleEntryUrlsTest {

  private static final HandlerContext CONTEXT = HandlerContext.of(List.of(), "p");

  private static final String CONDITIONAL = "Patient?identifier=sys|12345";

  private static final DeidentifierHandler<StringType> PSEUDONYMIZING =
      (value, context) -> Optional.of(new StringType("Patient/p-" + value.getValue().length()));

  private static final DeidentifierHandler<StringType> FAILING =
      (value, context) -> {
        throw new AssertionError("The reference rule must not be asked about " + value);
      };

  private static Patient deidentified(String id) {
    var patient = new Patient();
    patient.setId(id);
    return patient;
  }

  @Test
  void sendsAConditionalUrlOfAResourceEntryThroughTheHandler() {
    var entryUrls = BundleEntryUrls.of(PSEUDONYMIZING);

    var url = entryUrls.requestUrl(CONDITIONAL, deidentified("x9"), CONTEXT);

    assertThat(url).contains("Patient/p-28");
  }

  @Test
  void dropsAConditionalUrlOfAResourceEntryWhenTheHandlerRemovesIt() {
    var entryUrls = BundleEntryUrls.of((value, context) -> Optional.empty());

    assertThat(entryUrls.requestUrl(CONDITIONAL, deidentified("x9"), CONTEXT)).isEmpty();
  }

  @Test
  void dropsAConditionalUrlOfAResourceEntryWithoutAReferenceRule() {
    var entryUrls = BundleEntryUrls.none();

    assertThat(entryUrls.requestUrl(CONDITIONAL, deidentified("x9"), CONTEXT)).isEmpty();
  }

  @Test
  void takesTheIdOfTheDeidentifiedResourceWithoutAskingTheHandler() {
    var entryUrls = BundleEntryUrls.of(FAILING);

    assertThat(entryUrls.requestUrl("Patient/123", deidentified("x9"), CONTEXT))
        .contains("Patient/x9");
  }

  /** A FHIRPath patch carries a Parameters, a JSON patch a Binary. */
  private static Stream<Resource> patches() {
    return Stream.of(new Parameters().setId("patch-1"), new Binary().setId("patch-1"));
  }

  @ParameterizedTest
  @MethodSource("patches")
  void sendsTheUrlOfAPatchThroughTheHandlerBecauseItsResourceIsOfAnotherType(Resource patch) {
    var entryUrls = BundleEntryUrls.of(PSEUDONYMIZING);

    assertThat(entryUrls.requestUrl("Patient/123", patch, CONTEXT)).contains("Patient/p-11");
  }

  @Test
  void dropsTheUrlOfAPatchWithoutAReferenceRule() {
    assertThat(BundleEntryUrls.none().requestUrl("Patient/123", new Parameters(), CONTEXT))
        .isEmpty();
  }

  @Test
  void dropsTheUrlOfAPatchWhenTheHandlerRemovesIt() {
    var entryUrls = BundleEntryUrls.of((value, context) -> Optional.empty());

    assertThat(entryUrls.requestUrl("Patient/123", new Parameters(), CONTEXT)).isEmpty();
  }

  @Test
  void dropsATypeAndIdUrlWhenTheResourceLostItsId() {
    var entryUrls = BundleEntryUrls.of(FAILING);

    assertThat(entryUrls.requestUrl("Patient/123", new Patient(), CONTEXT)).isEmpty();
  }

  @Test
  void keepsATypeOnlyUrlOfAResourceEntryWithoutAskingTheHandler() {
    var entryUrls = BundleEntryUrls.of(FAILING);

    assertThat(entryUrls.requestUrl("Patient", deidentified("x9"), CONTEXT)).contains("Patient");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "Patient?name=Doe",
        CONDITIONAL + "&name=Doe",
        "Patient/123/_history/2",
        "https://server.example/fhir/Patient/123",
        ""
      })
  void dropsAnyOtherUrlOfAResourceEntryWithoutAskingTheHandler(String url) {
    var entryUrls = BundleEntryUrls.of(FAILING);

    assertThat(entryUrls.requestUrl(url, deidentified("x9"), CONTEXT)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"Patient/123", CONDITIONAL})
  void sendsTheUrlOfADeleteThroughTheHandler(String url) {
    var entryUrls = BundleEntryUrls.of(PSEUDONYMIZING);

    assertThat(entryUrls.requestUrl(url, CONTEXT)).contains("Patient/p-" + url.length());
  }

  @Test
  void dropsATypeOnlyDeleteBecauseItNamesNoSourceResource() {
    var entryUrls = BundleEntryUrls.of(FAILING);

    assertThat(entryUrls.requestUrl("Patient", CONTEXT)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"Patient?name=Doe", CONDITIONAL + "&name=Doe", "Patient/123/_history/2", ""})
  void dropsAnyOtherUrlOfADeleteWithoutAskingTheHandler(String url) {
    var entryUrls = BundleEntryUrls.of(FAILING);

    assertThat(entryUrls.requestUrl(url, CONTEXT)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {"Patient/123", CONDITIONAL})
  void dropsTheUrlOfADeleteWithoutAReferenceRule(String url) {
    assertThat(BundleEntryUrls.none().requestUrl(url, CONTEXT)).isEmpty();
  }

  @Test
  void dropsTheUrlOfADeleteWhenTheHandlerRemovesIt() {
    var entryUrls = BundleEntryUrls.of((value, context) -> Optional.empty());

    assertThat(entryUrls.requestUrl("Patient/123", CONTEXT)).isEmpty();
  }

  @Test
  void dropsTheUrlOfADeleteWhenTheHandlerReturnsAValuelessString() {
    var entryUrls = BundleEntryUrls.of((value, context) -> Optional.of(new StringType()));

    assertThat(entryUrls.requestUrl("Patient/123", CONTEXT)).isEmpty();
  }

  @Test
  void refusesANullReferenceHandler() {
    assertThatThrownBy(() -> BundleEntryUrls.of(null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void carriesThePseudonymizedReferenceOfAUrnUuidFullUrlAsAUuid() {
    var seen = new ArrayList<String>();
    var entryUrls =
        BundleEntryUrls.of(
            (value, context) -> {
              seen.add(value.getValue());
              return Optional.of(new StringType("Patient/SYNTH-1"));
            });

    var fullUrl = entryUrls.fullUrl("urn:uuid:source-chosen", CONTEXT);

    assertThat(seen).containsExactly("urn:uuid:source-chosen");
    assertThat(fullUrl).contains("urn:uuid:" + PseudonymUuid.uuidFrom("Patient/SYNTH-1"));
  }

  @Test
  void dropsAUrnUuidFullUrlWhenTheHandlerRemovesIt() {
    var entryUrls = BundleEntryUrls.of((value, context) -> Optional.empty());

    assertThat(entryUrls.fullUrl("urn:uuid:1", CONTEXT)).isEmpty();
  }

  @Test
  void dropsAUrnUuidFullUrlWhenTheHandlerReturnsAValuelessString() {
    var entryUrls = BundleEntryUrls.of((value, context) -> Optional.of(new StringType()));

    assertThat(entryUrls.fullUrl("urn:uuid:1", CONTEXT)).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"https://server.example/fhir/Patient/123", "urn:oid:1.2.3", "Patient/123", ""})
  void dropsAFullUrlThatIsNoUrnUuidWithoutAskingTheHandler(String fullUrl) {
    var entryUrls = BundleEntryUrls.of(FAILING);

    assertThat(entryUrls.fullUrl(fullUrl, CONTEXT)).isEmpty();
  }

  @Test
  void dropsEveryFullUrlWithoutAReferenceRule() {
    assertThat(BundleEntryUrls.none().fullUrl("urn:uuid:1", CONTEXT)).isEmpty();
  }

  @Test
  void givesTheSamePseudonymUnderTwoTypesTwoFullUrls() {
    var patient = BundleEntryUrls.of((value, context) -> Optional.of(new StringType("Patient/X")));
    var encounter =
        BundleEntryUrls.of((value, context) -> Optional.of(new StringType("Encounter/X")));

    assertThat(patient.fullUrl("urn:uuid:1", CONTEXT))
        .isNotEqualTo(encounter.fullUrl("urn:uuid:2", CONTEXT));
  }
}
