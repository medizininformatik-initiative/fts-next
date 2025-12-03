package care.smith.fts.cda.services.configuration;

import care.smith.fts.cda.services.PatientCompartmentService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Slf4j
@Configuration
public class PatientCompartmentServiceConfiguration {

  private static final String DEFAULT_COMPARTMENT_DEFINITION_PATH =
      "fhir/compartmentdefinition-patient.json";

  /** Override this method in tests to use a different resource path. */
  protected String getCompartmentDefinitionPath() {
    return DEFAULT_COMPARTMENT_DEFINITION_PATH;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record CompartmentDefinition(List<ResourceEntry> resource) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ResourceEntry(String code, List<String> param) {
    List<String> paramsOrEmpty() {
      return param != null ? param : List.of();
    }
  }

  @Bean
  public PatientCompartmentService patientCompartmentService(ObjectMapper objectMapper) {
    var resourceTypeToParams = loadCompartmentDefinition(objectMapper);
    log.info(
        "Loaded patient compartment service with {} resource types having compartment params",
        resourceTypeToParams.values().stream().filter(params -> !params.isEmpty()).count());
    return new PatientCompartmentService(resourceTypeToParams);
  }

  private Map<String, List<String>> loadCompartmentDefinition(ObjectMapper objectMapper) {
    try (InputStream is = new ClassPathResource(getCompartmentDefinitionPath()).getInputStream()) {
      CompartmentDefinition definition = objectMapper.readValue(is, CompartmentDefinition.class);

      if (definition.resource() == null) {
        throw new IllegalStateException("Invalid compartment definition: missing resource array");
      }

      return definition.resource().stream()
          .collect(
              Collectors.toUnmodifiableMap(
                  ResourceEntry::code, ResourceEntry::paramsOrEmpty, (a, b) -> a));
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load patient compartment definition", e);
    }
  }
}
