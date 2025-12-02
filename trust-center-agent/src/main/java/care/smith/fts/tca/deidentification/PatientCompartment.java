package care.smith.fts.tca.deidentification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Provides information about which FHIR resource types are in the Patient compartment.
 *
 * <p>Resources in the patient compartment have their IDs derived from patient salt. Resources not
 * in the compartment have their IDs pseudonymized via gPAS directly.
 */
@Slf4j
@Component
public class PatientCompartment {

  private static final String DEFAULT_COMPARTMENT_DEFINITION_PATH =
      "fhir/compartmentdefinition-patient.json";

  private final Set<String> compartmentResourceTypes;

  @JsonIgnoreProperties(ignoreUnknown = true)
  record CompartmentDefinition(List<ResourceEntry> resource) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ResourceEntry(String code, List<String> param) {
    boolean isInCompartment() {
      return param != null && !param.isEmpty();
    }
  }

  public PatientCompartment() {
    this(loadCompartmentResourceTypes(DEFAULT_COMPARTMENT_DEFINITION_PATH));
  }

  PatientCompartment(Set<String> compartmentResourceTypes) {
    this.compartmentResourceTypes = compartmentResourceTypes;
    log.info("Loaded patient compartment with {} resource types", compartmentResourceTypes.size());
  }

  /**
   * Checks if a resource type is in the patient compartment.
   *
   * @param resourceType the FHIR resource type (e.g., "Observation", "Organization")
   * @return true if the resource type has a param key in the compartment definition
   */
  public boolean isInPatientCompartment(String resourceType) {
    return compartmentResourceTypes.contains(resourceType);
  }

  /** Returns an unmodifiable view of all resource types in the patient compartment. */
  public Set<String> getCompartmentResourceTypes() {
    return Set.copyOf(compartmentResourceTypes);
  }

  static Set<String> loadCompartmentResourceTypes(String resourcePath) {
    try (InputStream is = new ClassPathResource(resourcePath).getInputStream()) {
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
