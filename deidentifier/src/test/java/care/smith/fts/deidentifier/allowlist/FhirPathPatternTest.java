package care.smith.fts.deidentifier.allowlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class FhirPathPatternTest {

  @ParameterizedTest(name = "{0}")
  @CsvSource(
      delimiter = ';',
      value = {
        "Patient.exists(); Patient",
        "Observation.meta.profile contains 'https://example.org/P'; Observation",
        "Patient.identifier.system contains 'https://example.org/S'; Patient"
      })
  void namesTheResourceTypeOfEveryPatternKind(String pattern, String resourceType) {
    assertThat(FhirPathPattern.parse(pattern).resourceType()).isEqualTo(resourceType);
  }

  @Test
  void existsPatternMatchesEveryResourceOfItsType() {
    FhirPathPattern pattern = FhirPathPattern.parse("Patient.exists()");

    assertThat(pattern.matches(new Patient())).isTrue();
  }

  @Test
  void existsPatternDoesNotMatchAnotherResourceType() {
    FhirPathPattern pattern = FhirPathPattern.parse("Patient.exists()");

    assertThat(pattern.matches(new Observation())).isFalse();
  }

  @Test
  void profilePatternMatchesAResourceThatClaimsTheProfile() {
    FhirPathPattern pattern =
        FhirPathPattern.parse("Patient.meta.profile contains 'https://example.org/Patient'");
    Patient patient = new Patient();
    patient.getMeta().addProfile("https://example.org/Patient");

    assertThat(pattern.matches(patient)).isTrue();
  }

  /** A resource may claim the version it was written against; a module may name it or not. */
  @ParameterizedTest(name = "pattern {0}, claimed {1} -> {2}")
  @CsvSource({
    "https://example.org/Patient, https://example.org/Patient|1.0, true",
    "https://example.org/Patient|1.0, https://example.org/Patient|1.0, true",
    "https://example.org/Patient|1.0, https://example.org/Patient|2.0, false",
    "https://example.org/Patient|1.0, https://example.org/Patient, false",
  })
  void profilePatternMatchesTheVersionsOfItsProfile(
      String patternProfile, String claimedProfile, boolean matches) {
    FhirPathPattern pattern =
        FhirPathPattern.parse("Patient.meta.profile contains '%s'".formatted(patternProfile));
    Patient patient = new Patient();
    patient.getMeta().addProfile(claimedProfile);

    assertThat(pattern.matches(patient)).isEqualTo(matches);
  }

  static Stream<Arguments> resourcesThatDoNotMatchAProfilePattern() {
    Observation otherType = new Observation();
    otherType.getMeta().addProfile("https://example.org/Patient");
    Patient otherProfile = new Patient();
    otherProfile.getMeta().addProfile("https://example.org/Patient2");
    Patient valuelessProfile = new Patient();
    valuelessProfile.getMeta().getProfile().add(new CanonicalType());
    return Stream.of(
        Arguments.of("another resource type", otherType),
        Arguments.of("a profile that starts with the same text", otherProfile),
        Arguments.of("a profile entry without a value", valuelessProfile));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("resourcesThatDoNotMatchAProfilePattern")
  void profilePatternDoesNotMatch(String description, Resource resource) {
    FhirPathPattern pattern =
        FhirPathPattern.parse("Patient.meta.profile contains 'https://example.org/Patient'");

    assertThat(pattern.matches(resource)).isFalse();
  }

  @Test
  void identifierPatternMatchesAResourceWithAnIdentifierOfTheSystem() {
    FhirPathPattern pattern =
        FhirPathPattern.parse("Patient.identifier.system contains 'https://example.org/mrn'");
    Patient patient = new Patient();
    patient.addIdentifier().setSystem("https://example.org/other");
    patient.addIdentifier().setSystem("https://example.org/mrn").setValue("42");

    assertThat(pattern.matches(patient)).isTrue();
  }

  static Stream<Arguments> resourcesThatDoNotMatchAnIdentifierPattern() {
    Observation otherType = new Observation();
    otherType.addIdentifier().setSystem("https://example.org/mrn");
    Patient otherSystem = new Patient();
    otherSystem.addIdentifier().setSystem("https://example.org/other");
    return Stream.of(
        Arguments.of("another resource type", otherType),
        Arguments.of("an identifier of another system", otherSystem),
        Arguments.of("no identifier", new Patient()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("resourcesThatDoNotMatchAnIdentifierPattern")
  void identifierPatternDoesNotMatch(String description, Resource resource) {
    FhirPathPattern pattern =
        FhirPathPattern.parse("Patient.identifier.system contains 'https://example.org/mrn'");

    assertThat(pattern.matches(resource)).isFalse();
  }

  /** QuestionnaireResponse holds a single identifier, not a list. */
  @Test
  void identifierPatternReadsASingleIdentifier() {
    FhirPathPattern pattern =
        FhirPathPattern.parse(
            "QuestionnaireResponse.identifier.system contains 'https://example.org/qr'");
    QuestionnaireResponse withIdentifier = new QuestionnaireResponse();
    withIdentifier.getIdentifier().setSystem("https://example.org/qr");

    assertThat(pattern.matches(withIdentifier)).isTrue();
    assertThat(pattern.matches(new QuestionnaireResponse())).isFalse();
  }

  /** Such a pattern could never match, so the profile is wrong. */
  @Test
  void parseRejectsAnIdentifierPatternOnATypeWithoutIdentifier() {
    assertThatThrownBy(
            () -> FhirPathPattern.parse("Binary.identifier.system contains 'https://example.org'"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Resource type Binary has no identifier element to match a system on.");
  }

  /**
   * A pattern on a type that the R4 model does not have can never match. HAPI resolves a type name
   * regardless of case, so a wrongly cased name has to be rejected as well.
   */
  @ParameterizedTest
  @CsvSource(
      delimiter = '|',
      value = {
        "Patinet.exists()                                     | Patinet",
        "patient.exists()                                     | patient",
        "Patinet.meta.profile contains 'https://example.org'  | Patinet",
        "Patinet.identifier.system contains 'https://example.org' | Patinet",
      })
  void parseRejectsAnUnknownResourceType(String fhirPath, String resourceType) {
    assertThatThrownBy(() -> FhirPathPattern.parse(fhirPath))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Pattern '%s' names no FHIR R4 resource type: %s".formatted(fhirPath, resourceType));
  }

  @Test
  void parseRejectsAPatternOfUnknownShape() {
    assertThatThrownBy(() -> FhirPathPattern.parse("Patient.gender = 'male'"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Patient.gender = 'male'");
  }
}
