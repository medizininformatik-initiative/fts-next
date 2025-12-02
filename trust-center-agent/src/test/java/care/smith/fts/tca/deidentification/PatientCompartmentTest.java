package care.smith.fts.tca.deidentification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PatientCompartmentTest {

  private PatientCompartment patientCompartment;

  @BeforeEach
  void setUp() {
    patientCompartment = new PatientCompartment();
  }

  @Test
  void loadsCompartmentDefinition() {
    assertThat(patientCompartment.getCompartmentResourceTypes()).isNotEmpty();
  }

  @Test
  void loadsCorrectNumberOfResourceTypes() {
    // The compartment definition has 66 resource types with param keys
    assertThat(patientCompartment.getCompartmentResourceTypes()).hasSize(66);
  }

  @Test
  void containsExpectedPatientCompartmentResources() {
    // Resources with param key in compartment definition
    assertThat(patientCompartment.isInPatientCompartment("Patient")).isTrue();
    assertThat(patientCompartment.isInPatientCompartment("Observation")).isTrue();
    assertThat(patientCompartment.isInPatientCompartment("Condition")).isTrue();
    assertThat(patientCompartment.isInPatientCompartment("Encounter")).isTrue();
    assertThat(patientCompartment.isInPatientCompartment("MedicationRequest")).isTrue();
    assertThat(patientCompartment.isInPatientCompartment("Procedure")).isTrue();
    assertThat(patientCompartment.isInPatientCompartment("DiagnosticReport")).isTrue();
    assertThat(patientCompartment.isInPatientCompartment("AllergyIntolerance")).isTrue();
  }

  @Test
  void excludesResourcesWithoutParamKey() {
    // Resources without param key in compartment definition
    assertThat(patientCompartment.isInPatientCompartment("Organization")).isFalse();
    assertThat(patientCompartment.isInPatientCompartment("Practitioner")).isFalse();
    assertThat(patientCompartment.isInPatientCompartment("Medication")).isFalse();
    assertThat(patientCompartment.isInPatientCompartment("Location")).isFalse();
    assertThat(patientCompartment.isInPatientCompartment("CodeSystem")).isFalse();
    assertThat(patientCompartment.isInPatientCompartment("ValueSet")).isFalse();
    assertThat(patientCompartment.isInPatientCompartment("StructureDefinition")).isFalse();
    assertThat(patientCompartment.isInPatientCompartment("Bundle")).isFalse();
  }

  @Test
  void handlesUnknownResourceType() {
    assertThat(patientCompartment.isInPatientCompartment("UnknownResource")).isFalse();
    assertThat(patientCompartment.isInPatientCompartment("")).isFalse();
  }

  @Test
  void isCaseSensitive() {
    assertThat(patientCompartment.isInPatientCompartment("patient")).isFalse();
    assertThat(patientCompartment.isInPatientCompartment("OBSERVATION")).isFalse();
    assertThat(patientCompartment.isInPatientCompartment("Patient")).isTrue();
    assertThat(patientCompartment.isInPatientCompartment("Observation")).isTrue();
  }

  @Test
  void compartmentResourceTypesIsImmutable() {
    var types = patientCompartment.getCompartmentResourceTypes();
    org.junit.jupiter.api.Assertions.assertThrows(
        UnsupportedOperationException.class, () -> types.add("NewType"));
  }

  @Nested
  class ErrorHandlingTests {

    @Test
    void throwsExceptionForMissingResourceFile() {
      assertThatThrownBy(() -> new PatientCompartment("fhir/nonexistent-file.json"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Failed to load patient compartment definition");
    }

    @Test
    void throwsExceptionForMissingResourceArray() {
      assertThatThrownBy(
              () -> new PatientCompartment("fhir/invalid-compartment-missing-resource.json"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Invalid compartment definition: missing resource array");
    }

    @Test
    void throwsExceptionForNonArrayResource() {
      assertThatThrownBy(() -> new PatientCompartment("fhir/invalid-compartment-no-array.json"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Invalid compartment definition: missing resource array");
    }
  }
}
