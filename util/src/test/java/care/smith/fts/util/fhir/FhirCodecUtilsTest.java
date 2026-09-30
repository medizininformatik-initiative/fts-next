package care.smith.fts.util.fhir;

import static care.smith.fts.util.fhir.FhirCodecUtils.ensureBaseResource;
import static care.smith.fts.util.fhir.FhirCodecUtils.isBaseResource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.core.ResolvableType.forClass;

import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

class FhirCodecUtilsTest {

  @Test
  void ensureBaseResourceReturnsResourceClass() {
    assertThat(ensureBaseResource(forClass(Patient.class))).isEqualTo(Patient.class);
  }

  @Test
  void ensureBaseResourceRejectsOtherClass() {
    assertThatThrownBy(() -> ensureBaseResource(forClass(String.class)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("String");
  }

  @Test
  void nullIsNoBaseResource() {
    assertThat(isBaseResource(null)).isFalse();
  }
}
