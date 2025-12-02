package care.smith.fts.tca.deidentification;

import java.util.Set;

/**
 * Provides information about which FHIR resource types are in the Patient compartment.
 *
 * <p>Resources in the patient compartment have their IDs derived from patient salt. Resources not
 * in the compartment have their IDs pseudonymized via gPAS directly.
 */
public class PatientCompartment {

  private final Set<String> compartmentResourceTypes;

  public PatientCompartment(Set<String> compartmentResourceTypes) {
    this.compartmentResourceTypes = compartmentResourceTypes;
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
}
