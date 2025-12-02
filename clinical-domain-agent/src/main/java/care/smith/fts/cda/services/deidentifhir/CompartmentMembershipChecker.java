package care.smith.fts.cda.services.deidentifhir;

import care.smith.fts.cda.services.PatientCompartmentService;
import java.util.List;
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
    return references.stream().anyMatch(ref -> referencesPatient(ref, patientId));
  }

  private List<Reference> getReferencesForParam(Resource resource, String paramName) {
    try {
      Property prop = resource.getNamedProperty(paramName);
      if (prop == null) {
        return List.of();
      }

      return prop.getValues().stream()
          .filter(Reference.class::isInstance)
          .map(Reference.class::cast)
          .toList();
    } catch (Exception e) {
      log.trace(
          "Could not get property '{}' from resource {}: {}",
          paramName,
          resource.fhirType(),
          e.getMessage());
      return List.of();
    }
  }

  private boolean referencesPatient(Reference reference, String patientId) {
    if (reference == null || reference.isEmpty()) {
      return false;
    }

    String refValue = reference.getReference();
    if (refValue == null) {
      return false;
    }

    // Check both "Patient/{patientId}" and just the ID part
    return refValue.equals("Patient/" + patientId) || extractIdFromReference(refValue, patientId);
  }

  private boolean extractIdFromReference(String refValue, String patientId) {
    // Handle reference like "Patient/123" -> extract "123"
    if (refValue.startsWith("Patient/")) {
      String id = refValue.substring("Patient/".length());
      return id.equals(patientId);
    }
    return false;
  }
}
