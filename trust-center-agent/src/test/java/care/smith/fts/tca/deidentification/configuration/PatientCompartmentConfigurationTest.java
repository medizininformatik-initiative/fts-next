package care.smith.fts.tca.deidentification.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import care.smith.fts.tca.deidentification.configuration.PatientCompartmentConfiguration.ResourceEntry;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PatientCompartmentConfigurationTest {

  private final PatientCompartmentConfiguration configuration =
      new PatientCompartmentConfiguration();

  @Test
  void loadsCompartmentDefinition() {
    var patientCompartment = configuration.patientCompartment();

    assertThat(patientCompartment.getCompartmentResourceTypes()).isNotEmpty();
  }

  @Test
  void loadsCorrectNumberOfResourceTypes() {
    var patientCompartment = configuration.patientCompartment();

    assertThat(patientCompartment.getCompartmentResourceTypes()).hasSize(66);
  }

  @Test
  void containsExpectedPatientCompartmentResources() {
    var patientCompartment = configuration.patientCompartment();

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
    var patientCompartment = configuration.patientCompartment();

    assertThat(patientCompartment.isInPatientCompartment("Organization")).isFalse();
    assertThat(patientCompartment.isInPatientCompartment("Practitioner")).isFalse();
    assertThat(patientCompartment.isInPatientCompartment("Medication")).isFalse();
    assertThat(patientCompartment.isInPatientCompartment("Location")).isFalse();
    assertThat(patientCompartment.isInPatientCompartment("CodeSystem")).isFalse();
    assertThat(patientCompartment.isInPatientCompartment("ValueSet")).isFalse();
    assertThat(patientCompartment.isInPatientCompartment("StructureDefinition")).isFalse();
    assertThat(patientCompartment.isInPatientCompartment("Bundle")).isFalse();
  }

  @Nested
  class ResourceEntryTests {

    @Test
    void isInCompartment_withNullParam_returnsFalse() {
      var entry = new ResourceEntry("TestResource", null);
      assertThat(entry.isInCompartment()).isFalse();
    }

    @Test
    void isInCompartment_withParams_returnsTrue() {
      var entry = new ResourceEntry("TestResource", List.of("subject", "performer"));
      assertThat(entry.isInCompartment()).isTrue();
    }

    @Test
    void isInCompartment_withEmptyParams_returnsFalse() {
      var entry = new ResourceEntry("TestResource", List.of());
      assertThat(entry.isInCompartment()).isFalse();
    }
  }

  @Nested
  class ErrorHandlingTests {

    @Test
    void missingResourceFile_throwsIllegalStateException() {
      var config =
          new PatientCompartmentConfiguration() {
            @Override
            protected String getCompartmentDefinitionPath() {
              return "fhir/non-existent-file.json";
            }
          };

      assertThatThrownBy(config::patientCompartment)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Failed to load patient compartment definition");
    }

    @Test
    void nullResourceArray_throwsIllegalStateException() {
      var config =
          new PatientCompartmentConfiguration() {
            @Override
            protected String getCompartmentDefinitionPath() {
              return "fhir/compartmentdefinition-null-resource.json";
            }
          };

      assertThatThrownBy(config::patientCompartment)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Invalid compartment definition: missing resource array");
    }
  }
}
