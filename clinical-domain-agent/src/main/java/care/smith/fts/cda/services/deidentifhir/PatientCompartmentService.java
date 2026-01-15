package care.smith.fts.cda.services.deidentifhir;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Property;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Checks if a FHIR resource instance is in the patient compartment by examining whether the
 * resource's compartment param fields reference the patient.
 *
 * <p>A resource is in the patient compartment if ANY of its defined param fields reference the
 * patient. For example, ServiceRequest has params ["subject", "performer"], so it's in the
 * compartment if either the subject OR the performer references the patient.
 */
@Slf4j
@Component
public class PatientCompartmentService {

  /**
   * Maps search parameter names to their corresponding field names. The compartment definition uses
   * search parameter names (e.g., "patient"), but resources use field names (e.g., "subject").
   */
  private static final Map<String, List<String>> SEARCH_PARAM_TO_FIELD =
      Map.ofEntries(
          Map.entry("patient", List.of("subject", "patient")),
          Map.entry("subject", List.of("subject")),
          Map.entry("policy-holder", List.of("policyHolder")));

  /**
   * Maps (resourceType, searchParam) to nested paths for cases where the reference is not at the
   * top level. Paths use dot notation (e.g., "participant.actor" means
   * resource.participant[].actor).
   */
  private static final Map<String, Map<String, List<String>>> NESTED_PATHS =
      Map.ofEntries(
          Map.entry("Appointment", Map.of("actor", List.of("participant.actor"))),
          Map.entry("CareTeam", Map.of("participant", List.of("participant.member"))),
          Map.entry("RequestGroup", Map.of("participant", List.of("action.participant.actor"))),
          Map.entry("Claim", Map.of("payee", List.of("payee.party"))),
          Map.entry("ExplanationOfBenefit", Map.of("payee", List.of("payee.party"))),
          Map.entry("Composition", Map.of("attester", List.of("attester.party"))),
          Map.entry("MedicationAdministration", Map.of("performer", List.of("performer.actor"))),
          Map.entry("Group", Map.of("member", List.of("member.entity"))),
          Map.entry("Patient", Map.of("link", List.of("link.other"))));

  private final Map<String, List<String>> patientCompartmentParams;

  public PatientCompartmentService(Map<String, List<String>> patientCompartmentParams) {
    this.patientCompartmentParams = patientCompartmentParams;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record CompartmentDefinition(List<ResourceEntry> resource) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ResourceEntry(String code, List<String> param) {
    public List<String> paramsOrEmpty() {
      return param != null ? param : List.of();
    }
  }

  /**
   * Loads the patient compartment definition from a classpath resource.
   *
   * @param objectMapper the ObjectMapper to use for JSON parsing
   * @param resourcePath the classpath resource path to load from
   * @return a map of resource type to compartment params
   */
  public static Map<String, List<String>> loadCompartmentDefinition(
      ObjectMapper objectMapper, String resourcePath) {
    try (InputStream is = new ClassPathResource(resourcePath).getInputStream()) {
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

  /**
   * Checks if a resource is in the patient compartment.
   *
   * @param resource the FHIR resource to check
   * @param patientId the patient ID to check against
   * @return true if the resource IS the patient or ANY param field references the patient
   */
  public boolean isInPatientCompartment(Resource resource, String patientId) {
    String resourceType = resource.fhirType();

    // Special case: if the resource IS the patient, it's always in the compartment
    if ("Patient".equals(resourceType) && patientId.equals(resource.getIdPart())) {
      log.trace("Resource Patient/{} IS the patient, in compartment", patientId);
      return true;
    }

    List<String> params = patientCompartmentParams.getOrDefault(resourceType, List.of());

    if (params.isEmpty()) {
      log.trace("Resource type {} has no compartment params, not in compartment", resourceType);
      return false;
    }

    for (String param : params) {
      if (paramReferencesPatient(resource, param, patientId)) {
        log.trace(
            "Resource {}/{} is in patient compartment via param '{}'",
            resourceType,
            resource.getIdPart(),
            param);
        return true;
      }
    }

    log.trace(
        "Resource {}/{} has no param referencing patient {}",
        resourceType,
        resource.getIdPart(),
        patientId);
    return false;
  }

  private boolean paramReferencesPatient(Resource resource, String paramName, String patientId) {
    List<Reference> references = getReferencesForParam(resource, paramName);
    log.trace(
        "Resource {}/{} param '{}' has {} references",
        resource.fhirType(),
        resource.getIdPart(),
        paramName,
        references.size());
    return references.stream().anyMatch(ref -> referencesPatient(ref, patientId));
  }

  private List<Reference> getReferencesForParam(Resource resource, String paramName) {
    String resourceType = resource.fhirType();

    // First, check for nested paths specific to this resource type
    List<Reference> nestedRefs = getReferencesFromNestedPaths(resource, resourceType, paramName);
    if (!nestedRefs.isEmpty()) {
      return nestedRefs;
    }

    // Fall back to top-level field lookup
    List<String> fieldNames = SEARCH_PARAM_TO_FIELD.getOrDefault(paramName, List.of(paramName));

    for (String fieldName : fieldNames) {
      try {
        Property prop = resource.getNamedProperty(fieldName);
        if (prop == null) {
          continue;
        }

        log.trace(
            "Property '{}' (for param '{}') in {}/{} has {} values, types: {}",
            fieldName,
            paramName,
            resource.fhirType(),
            resource.getIdPart(),
            prop.getValues().size(),
            prop.getValues().stream().map(v -> v.getClass().getSimpleName()).toList());

        List<Reference> refs =
            prop.getValues().stream()
                .filter(Reference.class::isInstance)
                .map(Reference.class::cast)
                .toList();
        if (!refs.isEmpty()) {
          return refs;
        }
      } catch (Exception e) {
        log.trace(
            "Could not get property '{}' from resource {}: {}",
            fieldName,
            resource.fhirType(),
            e.getMessage());
      }
    }

    log.trace(
        "No Reference found for param '{}' (tried fields: {}) in resource {}/{}",
        paramName,
        fieldNames,
        resource.fhirType(),
        resource.getIdPart());
    return List.of();
  }

  private List<Reference> getReferencesFromNestedPaths(
      Resource resource, String resourceType, String paramName) {
    List<Reference> allRefs =
        Optional.ofNullable(NESTED_PATHS.get(resourceType))
            .map(paramPaths -> paramPaths.get(paramName))
            .stream()
            .flatMap(List::stream)
            .flatMap(path -> traversePath(resource, path).stream())
            .toList();

    if (!allRefs.isEmpty()) {
      log.trace(
          "Found {} references via nested path for param '{}' in {}/{}",
          allRefs.size(),
          paramName,
          resourceType,
          resource.getIdPart());
    }
    return allRefs;
  }

  /**
   * Traverses a dot-separated path through a resource to find Reference values. Handles both single
   * values and lists at each level.
   */
  private List<Reference> traversePath(org.hl7.fhir.r4.model.Base current, String path) {
    if (current == null || path == null || path.isEmpty()) {
      return List.of();
    }

    String[] parts = path.split("\\.", 2);
    String fieldName = parts[0];
    String remainingPath = parts.length > 1 ? parts[1] : null;

    try {
      Property prop = current.getNamedProperty(fieldName);
      if (prop == null || prop.getValues().isEmpty()) {
        return List.of();
      }

      // If this is the last part of the path, extract References
      if (remainingPath == null) {
        return prop.getValues().stream()
            .filter(Reference.class::isInstance)
            .map(Reference.class::cast)
            .toList();
      }

      // Otherwise, continue traversing each value
      List<Reference> results = new java.util.ArrayList<>();
      for (org.hl7.fhir.r4.model.Base value : prop.getValues()) {
        results.addAll(traversePath(value, remainingPath));
      }
      return results;
    } catch (Exception e) {
      log.trace("Error traversing path '{}' from {}: {}", path, current.fhirType(), e.getMessage());
      return List.of();
    }
  }

  private boolean referencesPatient(Reference reference, String patientId) {
    if (reference.isEmpty()) {
      return false;
    }

    String refValue = reference.getReference();
    if (refValue == null) {
      return false;
    }

    log.trace("Checking reference '{}' against patient ID '{}'", refValue, patientId);

    // Handle various reference formats:
    // - "Patient/ID"
    // - "http://server/fhir/Patient/ID"
    // - Full URLs with Patient resource
    String patientRef = "Patient/" + patientId;
    if (refValue.equals(patientRef) || refValue.endsWith("/" + patientRef)) {
      return true;
    }

    // Also check if the reference ends with just the patient ID after "Patient/"
    return extractIdFromReference(refValue, patientId);
  }

  private boolean extractIdFromReference(String refValue, String patientId) {
    // Find "Patient/" in the reference and extract the ID that follows
    int patientIdx = refValue.lastIndexOf("Patient/");
    if (patientIdx >= 0) {
      String id = refValue.substring(patientIdx + "Patient/".length());
      // Remove any trailing path components or query params
      int slashIdx = id.indexOf('/');
      if (slashIdx > 0) {
        id = id.substring(0, slashIdx);
      }
      int queryIdx = id.indexOf('?');
      if (queryIdx > 0) {
        id = id.substring(0, queryIdx);
      }
      return id.equals(patientId);
    }
    return false;
  }
}
