package care.smith.fts.tca.deidentification.configuration;

import care.smith.fts.tca.deidentification.PatientCompartment;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Slf4j
@Configuration
public class PatientCompartmentConfiguration {

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
    boolean isInCompartment() {
      return param != null && !param.isEmpty();
    }
  }

  @Bean
  public PatientCompartment patientCompartment() {
    var resourceTypes = loadCompartmentResourceTypes();
    log.info("Loaded patient compartment with {} resource types", resourceTypes.size());
    return new PatientCompartment(resourceTypes);
  }

  private Set<String> loadCompartmentResourceTypes() {
    try (InputStream is = new ClassPathResource(getCompartmentDefinitionPath()).getInputStream()) {
      ObjectMapper mapper = new ObjectMapper();
      CompartmentDefinition definition = mapper.readValue(is, CompartmentDefinition.class);

      if (definition.resource() == null) {
        throw new IllegalStateException("Invalid compartment definition: missing resource array");
      }

      return definition.resource().stream()
          .filter(ResourceEntry::isInCompartment)
          .map(ResourceEntry::code)
          .collect(Collectors.toUnmodifiableSet());
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load patient compartment definition", e);
    }
  }
}
