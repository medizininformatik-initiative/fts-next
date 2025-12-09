package care.smith.fts.tca.adapters;

import java.util.Map;
import java.util.Set;
import reactor.core.publisher.Mono;

/**
 * Interface for pseudonymization backend adapters.
 *
 * <p>This interface defines the contract for adapters that communicate with pseudonymization
 * backends such as gPAS, Vfps, or entici. Each adapter implementation translates requests to the
 * backend-specific protocol and returns the generated pseudonyms.
 *
 * <p>All operations are reactive and return {@link Mono} to support non-blocking processing.
 */
public interface PseudonymBackendAdapter {

  /**
   * Fetches or creates a single pseudonym for the given original value in the specified domain.
   *
   * @param domain the pseudonymization domain/namespace
   * @param originalValue the original value to pseudonymize
   * @return a Mono emitting the generated pseudonym (sID)
   */
  Mono<String> fetchOrCreatePseudonym(String domain, String originalValue);

  /**
   * Fetches or creates pseudonyms for multiple original values in the specified domain.
   *
   * <p>This batch operation is more efficient than calling {@link #fetchOrCreatePseudonym} multiple
   * times when processing many identifiers.
   *
   * @param domain the pseudonymization domain/namespace
   * @param originalValues the set of original values to pseudonymize
   * @return a Mono emitting a map from original value to generated pseudonym (sID)
   */
  Mono<Map<String, String>> fetchOrCreatePseudonyms(String domain, Set<String> originalValues);

  /**
   * Returns the backend type identifier for this adapter.
   *
   * @return the backend type (e.g., "gpas", "vfps", "entici")
   */
  String getBackendType();
}
