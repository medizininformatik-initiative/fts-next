package care.smith.fts.tca.adapters;

import care.smith.fts.tca.deidentification.GpasClient;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Backend adapter for gPAS (generic Pseudonym Administration Service).
 *
 * <p>This adapter wraps the existing {@link GpasClient} to implement the {@link
 * PseudonymBackendAdapter} interface, enabling gPAS to be used as a backend for the FHIR
 * Pseudonymizer integration.
 *
 * <p>The adapter reuses all existing gPAS configuration and batching logic from GpasClient.
 */
@Slf4j
public class GpasBackendAdapter implements PseudonymBackendAdapter {

  private static final String BACKEND_TYPE = "gpas";

  private final GpasClient gpasClient;

  public GpasBackendAdapter(GpasClient gpasClient) {
    this.gpasClient = gpasClient;
  }

  @Override
  public Mono<String> fetchOrCreatePseudonym(String domain, String originalValue) {
    log.trace("Fetching pseudonym from gPAS: domain={}, originalValue={}", domain, originalValue);
    return gpasClient
        .fetchOrCreatePseudonyms(domain, Set.of(originalValue))
        .map(mappings -> mappings.get(originalValue))
        .doOnSuccess(
            pseudonym ->
                log.trace(
                    "Received pseudonym from gPAS: original={} -> pseudonym={}",
                    originalValue,
                    pseudonym));
  }

  @Override
  public Mono<Map<String, String>> fetchOrCreatePseudonyms(
      String domain, Set<String> originalValues) {
    log.trace("Fetching {} pseudonyms from gPAS: domain={}", originalValues.size(), domain);
    return gpasClient
        .fetchOrCreatePseudonyms(domain, originalValues)
        .doOnSuccess(
            mappings ->
                log.trace(
                    "Received {} pseudonyms from gPAS for domain={}", mappings.size(), domain));
  }

  @Override
  public String getBackendType() {
    return BACKEND_TYPE;
  }
}
