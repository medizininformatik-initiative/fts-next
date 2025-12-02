package care.smith.fts.tca.deidentification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class PatientCompartmentTest {

  @Test
  void isInPatientCompartmentReturnsTrue() {
    var patientCompartment = new PatientCompartment(Set.of("Patient", "Observation"));

    assertThat(patientCompartment.isInPatientCompartment("Patient")).isTrue();
    assertThat(patientCompartment.isInPatientCompartment("Observation")).isTrue();
  }

  @Test
  void isInPatientCompartmentReturnsFalse() {
    var patientCompartment = new PatientCompartment(Set.of("Patient", "Observation"));

    assertThat(patientCompartment.isInPatientCompartment("Organization")).isFalse();
    assertThat(patientCompartment.isInPatientCompartment("UnknownResource")).isFalse();
    assertThat(patientCompartment.isInPatientCompartment("")).isFalse();
  }

  @Test
  void isCaseSensitive() {
    var patientCompartment = new PatientCompartment(Set.of("Patient", "Observation"));

    assertThat(patientCompartment.isInPatientCompartment("patient")).isFalse();
    assertThat(patientCompartment.isInPatientCompartment("OBSERVATION")).isFalse();
  }

  @Test
  void compartmentResourceTypesIsImmutable() {
    var patientCompartment = new PatientCompartment(Set.of("Patient"));

    var types = patientCompartment.getCompartmentResourceTypes();
    org.junit.jupiter.api.Assertions.assertThrows(
        UnsupportedOperationException.class, () -> types.add("NewType"));
  }

  @Test
  void getCompartmentResourceTypesReturnsAllTypes() {
    var patientCompartment = new PatientCompartment(Set.of("Patient", "Observation", "Condition"));

    assertThat(patientCompartment.getCompartmentResourceTypes())
        .containsExactlyInAnyOrder("Patient", "Observation", "Condition");
  }
}
