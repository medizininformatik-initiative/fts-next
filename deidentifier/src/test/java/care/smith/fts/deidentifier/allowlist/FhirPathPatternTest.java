package care.smith.fts.deidentifier.allowlist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

class FhirPathPatternTest {

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
  void parseRejectsAPatternOfUnknownShape() {
    assertThatThrownBy(() -> FhirPathPattern.parse("Patient.gender = 'male'"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Patient.gender = 'male'");
  }
}
