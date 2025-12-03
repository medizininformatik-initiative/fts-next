package care.smith.fts.cda.services.deidentifhir;

import care.smith.fts.cda.services.PatientCompartmentService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Property;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
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
@RequiredArgsConstructor
public class CompartmentMembershipChecker {

  /**
   * Maps search parameter names to their corresponding field names. The compartment definition uses
   * search parameter names (e.g., "patient"), but resources use field names (e.g., "subject").
   */
  private static final Map<String, List<String>> SEARCH_PARAM_TO_FIELD =
      Map.of(
          "patient", List.of("subject", "patient"),
          "subject", List.of("subject"));

  private final PatientCompartmentService patientCompartmentService;

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

    List<String> params = patientCompartmentService.getParamsForResourceType(resourceType);

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
    // Get possible field names for this search parameter
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

  private boolean referencesPatient(Reference reference, String patientId) {
    if (reference == null || reference.isEmpty()) {
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
