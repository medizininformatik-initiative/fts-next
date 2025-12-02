package care.smith.fts.tca.deidentification;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Splits resource IDs into patient-compartment and non-compartment categories.
 *
 * <p>IDs are categorized based on the presence of a patient ID prefix:
 *
 * <ul>
 *   <li>Patient-compartment: IDs starting with "{patientId}." (e.g., "patient1.Observation:obs123")
 *   <li>Non-compartment: IDs without patient prefix (e.g., "Organization:org456")
 * </ul>
 *
 * <p>The CDA determines actual compartment membership by checking if the resource's compartment
 * param fields reference the patient. The TCA only needs to check the prefix.
 */
@Component
public class CompartmentIdSplitter {

  public record CompartmentIds(Set<String> inCompartment, Set<String> outsideCompartment) {}

  /**
   * Splits resource IDs into patient-compartment and non-compartment sets based on patient ID
   * prefix.
   *
   * @param resourceIds the set of resource IDs to split
   * @param patientId the patient ID to check for as prefix
   * @return record containing IDs in and outside the patient compartment
   */
  public CompartmentIds split(Set<String> resourceIds, String patientId) {
    String prefix = patientId + ".";
    Map<Boolean, Set<String>> partitioned =
        resourceIds.stream()
            .collect(Collectors.partitioningBy(id -> id.startsWith(prefix), Collectors.toSet()));
    return new CompartmentIds(partitioned.get(true), partitioned.get(false));
  }
}
