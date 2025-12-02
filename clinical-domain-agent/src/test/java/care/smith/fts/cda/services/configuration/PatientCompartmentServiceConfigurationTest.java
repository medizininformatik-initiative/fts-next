package care.smith.fts.cda.services.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import care.smith.fts.cda.services.PatientCompartmentService;
import care.smith.fts.cda.services.configuration.PatientCompartmentServiceConfiguration.ResourceEntry;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PatientCompartmentServiceConfigurationTest {

  @Test
  void loadsCompartmentDefinitionFromClasspath() {
    var config = new PatientCompartmentServiceConfiguration();
    PatientCompartmentService service = config.patientCompartmentService();

    // Verify ServiceRequest has correct params from compartment definition
    assertThat(service.getParamsForResourceType("ServiceRequest"))
        .containsExactlyInAnyOrder("subject", "performer");

    // Verify Observation has correct params
    assertThat(service.getParamsForResourceType("Observation"))
        .containsExactlyInAnyOrder("subject", "performer");

    // Verify Organization has no params (not in compartment)
    assertThat(service.getParamsForResourceType("Organization")).isEmpty();

    // Verify Medication has no params (not in compartment)
    assertThat(service.getParamsForResourceType("Medication")).isEmpty();

    // Verify Patient has link param
    assertThat(service.getParamsForResourceType("Patient")).containsExactly("link");

    // Verify hasCompartmentParams works correctly
    assertThat(service.hasCompartmentParams("ServiceRequest")).isTrue();
    assertThat(service.hasCompartmentParams("Organization")).isFalse();
  }

  @Nested
  class ResourceEntryTests {

    @Test
    void paramsOrEmpty_withNullParam_returnsEmptyList() {
      var entry = new ResourceEntry("TestResource", null);
      assertThat(entry.paramsOrEmpty()).isEmpty();
    }

    @Test
    void paramsOrEmpty_withParams_returnsList() {
      var entry = new ResourceEntry("TestResource", List.of("subject", "performer"));
      assertThat(entry.paramsOrEmpty()).containsExactly("subject", "performer");
    }

    @Test
    void paramsOrEmpty_withEmptyParams_returnsEmptyList() {
      var entry = new ResourceEntry("TestResource", List.of());
      assertThat(entry.paramsOrEmpty()).isEmpty();
    }
  }

  @Nested
  class ErrorHandlingTests {

    @Test
    void missingResourceFile_throwsIllegalStateException() {
      var config =
          new PatientCompartmentServiceConfiguration() {
            @Override
            protected String getCompartmentDefinitionPath() {
              return "fhir/non-existent-file.json";
            }
          };

      assertThatThrownBy(config::patientCompartmentService)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Failed to load patient compartment definition");
    }

    @Test
    void nullResourceArray_throwsIllegalStateException() {
      var config =
          new PatientCompartmentServiceConfiguration() {
            @Override
            protected String getCompartmentDefinitionPath() {
              return "fhir/compartmentdefinition-null-resource.json";
            }
          };

      assertThatThrownBy(config::patientCompartmentService)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Invalid compartment definition: missing resource array");
    }

    @Test
    void duplicateResourceCodes_firstOneWins() {
      var config =
          new PatientCompartmentServiceConfiguration() {
            @Override
            protected String getCompartmentDefinitionPath() {
              return "fhir/compartmentdefinition-with-duplicates.json";
            }
          };

      var service = config.patientCompartmentService();

      // First entry for TestResource has ["subject"], second has ["performer"]
      // The merge function (a, b) -> a means first one wins
      assertThat(service.getParamsForResourceType("TestResource")).containsExactly("subject");
    }
  }
}
