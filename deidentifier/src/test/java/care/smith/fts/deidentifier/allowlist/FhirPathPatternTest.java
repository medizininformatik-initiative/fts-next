package care.smith.fts.deidentifier.allowlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.junit.jupiter.api.Test;

class FhirPathPatternTest {

  private static FhirPathPattern patientIdentifierSystem() {
    return FhirPathPattern.parse(
        "Patient.identifier.system contains 'https://example.org/fhir/sid'");
  }

  @Test
  void identifierSystemPatternMatchesResourceWithAnIdentifierOfThatSystem() {
    Patient patient = new Patient();
    patient.addIdentifier().setSystem("https://example.org/other");
    patient.addIdentifier().setSystem("https://example.org/fhir/sid").setValue("123");

    assertThat(patientIdentifierSystem().matches(patient)).isTrue();
  }

  @Test
  void identifierSystemPatternDoesNotMatchWithoutAMatchingIdentifier() {
    Patient withOtherSystem = new Patient();
    withOtherSystem.addIdentifier().setSystem("https://example.org/other");

    assertThat(patientIdentifierSystem().matches(withOtherSystem)).isFalse();
    assertThat(patientIdentifierSystem().matches(new Patient())).isFalse();
  }

  /** Some resource types carry at most one identifier, as a single element and not a list. */
  @Test
  void identifierSystemPatternMatchesAResourceWithASingleIdentifier() {
    QuestionnaireResponse response = new QuestionnaireResponse();
    response.setIdentifier(new Identifier().setSystem("https://example.org/fhir/sid"));

    assertThat(
            FhirPathPattern.parse(
                    "QuestionnaireResponse.identifier.system contains"
                        + " 'https://example.org/fhir/sid'")
                .matches(response))
        .isTrue();
  }

  /** Provenance has no identifier; such a pattern can never match and is a configuration error. */
  @Test
  void parseRejectsAnIdentifierPatternOnATypeWithoutIdentifier() {
    assertThatThrownBy(
            () -> FhirPathPattern.parse("Provenance.identifier.system contains 'https://sys'"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Provenance")
        .hasMessageContaining("identifier");
  }

  /**
   * A resource may pin the version of the profile it claims, {@code …/Diagnose|1.0.4}. The module
   * that governs it names the profile without a version, and both have to meet.
   */
  @Test
  void profilePatternMatchesAVersionedCanonicalOfTheSameProfile() {
    FhirPathPattern unversioned =
        FhirPathPattern.parse("Patient.meta.profile contains 'https://example.org/Person'");
    Patient versioned = new Patient();
    versioned.getMeta().addProfile("https://example.org/Person|1.0.4");
    Patient unversionedResource = new Patient();
    unversionedResource.getMeta().addProfile("https://example.org/Person");

    assertThat(unversioned.matches(versioned)).isTrue();
    assertThat(unversioned.matches(unversionedResource)).isTrue();
  }

  @Test
  void profilePatternWithAVersionMatchesThatVersionOnly() {
    FhirPathPattern pinned =
        FhirPathPattern.parse("Patient.meta.profile contains 'https://example.org/Person|1.0.4'");
    Patient sameVersion = new Patient();
    sameVersion.getMeta().addProfile("https://example.org/Person|1.0.4");
    Patient otherVersion = new Patient();
    otherVersion.getMeta().addProfile("https://example.org/Person|2.0.0");

    assertThat(pinned.matches(sameVersion)).isTrue();
    assertThat(pinned.matches(otherVersion)).isFalse();
  }

  @Test
  void profilePatternDoesNotMatchAProfileThatOnlySharesAPrefix() {
    FhirPathPattern pattern =
        FhirPathPattern.parse("Patient.meta.profile contains 'https://example.org/Person'");
    Patient other = new Patient();
    other.getMeta().addProfile("https://example.org/PersonGroup");

    assertThat(pattern.matches(other)).isFalse();
  }

  @Test
  void identifierSystemPatternDoesNotMatchAnotherResourceType() {
    Observation observation = new Observation();
    observation.addIdentifier().setSystem("https://example.org/fhir/sid");

    assertThat(patientIdentifierSystem().matches(observation)).isFalse();
  }

  @Test
  void parseRejectsAPatternOfUnknownShape() {
    assertThatThrownBy(() -> FhirPathPattern.parse("Patient.gender = 'male'"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Patient.gender = 'male'");
  }

  @Test
  void profilePatternDoesNotMatchAnotherResourceType() {
    FhirPathPattern pattern =
        FhirPathPattern.parse("Patient.meta.profile contains 'https://example.org/Person'");
    Observation observation = new Observation();
    observation.getMeta().addProfile("https://example.org/Person");

    assertThat(pattern.matches(observation)).isFalse();
  }

  @Test
  void profilePatternIgnoresAProfileWithoutValue() {
    FhirPathPattern pattern =
        FhirPathPattern.parse("Patient.meta.profile contains 'https://example.org/Person'");
    Patient patient = new Patient();
    patient.getMeta().getProfile().add(new CanonicalType());

    assertThat(pattern.matches(patient)).isFalse();
  }
}
