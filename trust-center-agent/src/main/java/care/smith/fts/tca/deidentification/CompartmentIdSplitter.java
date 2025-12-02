package care.smith.fts.tca.deidentification;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Splits resource IDs into patient-compartment and non-compartment categories.
 *
 * <p>IDs are categorized based on the FHIR Patient compartment definition:
 *
 * <ul>
 *   <li>Patient-compartment: resources with a param key in the compartment definition
 *   <li>Non-compartment: resources like Organization, Practitioner, Medication
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class CompartmentIdSplitter {

  // Pattern to extract first segment after prefix: {patientId}.{segment}...
  // For resource IDs: {patientId}.{ResourceType}:{id} → captures ResourceType
  // For identifiers: {patientId}.identifier.{system}:{value} → captures "identifier"
  private static final Pattern RESOURCE_ID_PATTERN = Pattern.compile("^[^.]+\\.([^.:]+)");

  private final PatientCompartment patientCompartment;

  public record CompartmentIds(Set<String> inCompartment, Set<String> outsideCompartment) {}

  /** Splits resource IDs into patient-compartment and non-compartment sets. */
  public CompartmentIds split(Set<String> resourceIds) {
    Map<Boolean, Set<String>> partitioned =
        resourceIds.stream()
            .collect(Collectors.partitioningBy(this::isInPatientCompartment, Collectors.toSet()));
    return new CompartmentIds(partitioned.get(true), partitioned.get(false));
  }

  /**
   * Checks if a resource ID belongs to the patient compartment.
   *
   * <p>IDs are in the patient compartment if:
   *
   * <ul>
   *   <li>They are identifiers (format: {patientId}.identifier.{system}:{value})
   *   <li>Their resource type has a param key in the compartment definition
   * </ul>
   */
  boolean isInPatientCompartment(String resourceId) {
    return extractResourceType(resourceId)
        .map(
            resourceType -> {
              if ("identifier".equals(resourceType)) {
                return true;
              }
              return patientCompartment.isInPatientCompartment(resourceType);
            })
        .orElse(true);
  }

  /**
   * Extracts the resource type from a namespaced resource ID.
   *
   * @param resourceId format: {patientId}.{ResourceType}:{id} or
   *     {patientId}.identifier.{system}:{value}
   * @return the resource type, or empty if the ID doesn't match the expected format
   */
  static Optional<String> extractResourceType(String resourceId) {
    Matcher matcher = RESOURCE_ID_PATTERN.matcher(resourceId);
    if (matcher.find()) {
      return Optional.of(matcher.group(1));
    }
    return Optional.empty();
  }
}
