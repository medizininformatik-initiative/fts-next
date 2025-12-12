package care.smith.fts.tca.rest;

import static java.util.Objects.requireNonNull;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

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
 */
public record VfpsPseudonymizeRequest(
    @NotBlank String namespace, @NotEmpty List<String> originals) {

  public VfpsPseudonymizeRequest {
    requireNonNull(namespace, "namespace is required");
    if (namespace.isBlank()) {
      throw new IllegalArgumentException("namespace must not be blank");
    }
    requireNonNull(originals, "originals is required");
    if (originals.isEmpty()) {
      throw new IllegalArgumentException("at least one original value required");
    }
    originals = List.copyOf(originals);
  }
}
