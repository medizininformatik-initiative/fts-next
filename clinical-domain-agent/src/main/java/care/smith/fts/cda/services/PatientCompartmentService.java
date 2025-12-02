package care.smith.fts.cda.services;

import java.util.List;
import java.util.Map;

/**
 * Provides information about which FHIR resource types are in the Patient compartment and which
 * search parameters link them to a patient.
 *
 * <p>The compartment definition specifies for each resource type which reference fields (params)
 * can link the resource to a patient. For example, ServiceRequest has params ["subject",
 * "performer"], meaning it's in the patient compartment if either field references the patient.
 */
public class PatientCompartmentService {

  private final Map<String, List<String>> resourceTypeToParams;

  public PatientCompartmentService(Map<String, List<String>> resourceTypeToParams) {
    this.resourceTypeToParams = resourceTypeToParams;
  }

  /**
   * Returns the param names that link a resource type to a patient.
   *
   * @param resourceType the FHIR resource type (e.g., "ServiceRequest", "Organization")
   * @return list of param names (e.g., ["subject", "performer"]), empty if not in compartment
   */
  public List<String> getParamsForResourceType(String resourceType) {
    return resourceTypeToParams.getOrDefault(resourceType, List.of());
  }

  /**
   * Checks if a resource type can be in the patient compartment.
   *
   * @param resourceType the FHIR resource type (e.g., "ServiceRequest", "Organization")
   * @return true if the resource type has compartment params
   */
  public boolean hasCompartmentParams(String resourceType) {
    return !getParamsForResourceType(resourceType).isEmpty();
  }
}
