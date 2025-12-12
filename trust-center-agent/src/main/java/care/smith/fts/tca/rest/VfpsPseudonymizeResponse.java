package care.smith.fts.tca.rest;

import java.util.List;

/**
 * Vfps-compatible pseudonymization response to be converted to FHIR Parameters.
 *
 * <p>This DTO represents the internal representation of a Vfps $create-pseudonym response before
 * serialization to FHIR Parameters. The response contains pseudonym entries with:
 *
 * <ul>
 *   <li>namespace - The original namespace from the request
 *   <li>originalValue - The original value that was pseudonymized
 *   <li>pseudonymValue - The generated pseudonym (transport ID for CDA, real sID for RDA)
 * </ul>
 *
 * @param pseudonyms List of pseudonym entries
 */
public record VfpsPseudonymizeResponse(List<PseudonymEntry> pseudonyms) {

  public VfpsPseudonymizeResponse {
    pseudonyms = pseudonyms != null ? List.copyOf(pseudonyms) : List.of();
  }

  /**
   * A single pseudonym mapping entry.
   *
   * @param namespace The domain/namespace
   * @param original The original value
   * @param pseudonym The generated pseudonym (tID or sID depending on endpoint)
   */
  public record PseudonymEntry(String namespace, String original, String pseudonym) {}
}
