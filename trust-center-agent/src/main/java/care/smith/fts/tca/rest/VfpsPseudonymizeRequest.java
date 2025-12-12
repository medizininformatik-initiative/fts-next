package care.smith.fts.tca.rest;

import java.util.List;
import java.util.Objects;

/**
 * Vfps-compatible pseudonymization request parsed from FHIR Parameters.
 *
 * <p>This DTO represents the internal representation of a Vfps $create-pseudonym request after
 * parsing the FHIR Parameters resource. The original request contains:
 *
 * <ul>
 *   <li>namespace - The domain/namespace for pseudonym generation
 *   <li>originalValue - One or more original values to pseudonymize
 * </ul>
 *
 * @param namespace The domain/namespace for pseudonym generation (non-blank)
 * @param originals The list of original values to pseudonymize (at least one)
 * @param transferId The transfer session identifier for grouping mappings
 */
public record VfpsPseudonymizeRequest(String namespace, List<String> originals, String transferId) {

  public VfpsPseudonymizeRequest {
    Objects.requireNonNull(namespace, "namespace is required");
    if (namespace.isBlank()) {
      throw new IllegalArgumentException("namespace must not be blank");
    }
    if (originals == null || originals.isEmpty()) {
      throw new IllegalArgumentException("at least one original value required");
    }
    Objects.requireNonNull(transferId, "transferId is required");
    originals = List.copyOf(originals);
  }
}
