package care.smith.fts.cda.services.deidentifhir.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import care.smith.fts.cda.services.deidentifhir.PatientCompartmentService;
import care.smith.fts.cda.services.deidentifhir.PatientCompartmentService.ResourceEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PatientCompartmentServiceConfigurationTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void loadsCompartmentDefinitionFromClasspath() {
    var config = new PatientCompartmentServiceConfiguration();
    Map<String, List<String>> params = config.patientCompartmentParams(objectMapper);

    // Verify ServiceRequest has correct params from compartment definition
    assertThat(params.getOrDefault("ServiceRequest", List.of()))
        .containsExactlyInAnyOrder("subject", "performer");

    // Verify Observation has correct params
    assertThat(params.getOrDefault("Observation", List.of()))
        .containsExactlyInAnyOrder("subject", "performer");

    // Verify Organization has no params (not in compartment)
    assertThat(params.getOrDefault("Organization", List.of())).isEmpty();

    // Verify Medication has no params (not in compartment)
    assertThat(params.getOrDefault("Medication", List.of())).isEmpty();

    // Verify Patient has link param
    assertThat(params.getOrDefault("Patient", List.of())).containsExactly("link");
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
      assertThatThrownBy(
              () ->
                  PatientCompartmentService.loadCompartmentDefinition(
                      objectMapper, "fhir/non-existent-file.json"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Failed to load patient compartment definition");
    }

    @Test
    void nullResourceArray_throwsIllegalStateException() {
      assertThatThrownBy(
              () ->
                  PatientCompartmentService.loadCompartmentDefinition(
                      objectMapper, "fhir/compartmentdefinition-null-resource.json"))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Invalid compartment definition: missing resource array");
    }

    @Test
    void duplicateResourceCodes_firstOneWins() {
      var params =
          PatientCompartmentService.loadCompartmentDefinition(
              objectMapper, "fhir/compartmentdefinition-with-duplicates.json");

      // First entry for TestResource has ["subject"], second has ["performer"]
      // The merge function (a, b) -> a means first one wins
      assertThat(params.getOrDefault("TestResource", List.of())).containsExactly("subject");
    }
  }
}
