package care.smith.fts.tca.deidentification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
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

  public PatientCompartment() {
    this(DEFAULT_COMPARTMENT_DEFINITION_PATH);
  }

  /** Package-private constructor for testing with custom resource path. */
  PatientCompartment(String resourcePath) {
    this.compartmentResourceTypes = loadCompartmentResourceTypes(resourcePath);
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

  private Set<String> loadCompartmentResourceTypes(String resourcePath) {
    try (InputStream is = new ClassPathResource(resourcePath).getInputStream()) {
      ObjectMapper mapper = new ObjectMapper();
      JsonNode root = mapper.readTree(is);
      JsonNode resources = root.get("resource");

      if (resources == null || !resources.isArray()) {
        throw new IllegalStateException("Invalid compartment definition: missing resource array");
      }

      return StreamSupport.stream(resources.spliterator(), false)
          .filter(node -> node.has("param"))
          .map(node -> node.get("code").asText())
          .collect(Collectors.toUnmodifiableSet());
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load patient compartment definition", e);
    }
  }
}
