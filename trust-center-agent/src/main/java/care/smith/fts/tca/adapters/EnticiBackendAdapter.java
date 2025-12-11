package care.smith.fts.tca.adapters;

import care.smith.fts.tca.deidentification.EnticiClient;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Backend adapter for Entici pseudonymization service.
 *
 * <p>This adapter communicates with an external Entici service to generate pseudonyms. It
 * implements the {@link PseudonymBackendAdapter} interface for use with the FHIR Pseudonymizer
 * integration.
 */
@Slf4j
public class EnticiBackendAdapter implements PseudonymBackendAdapter {

  private static final String BACKEND_TYPE = "entici";
  private final EnticiClient enticiClient;

  public EnticiBackendAdapter(EnticiClient enticiClient) {
    this.enticiClient = enticiClient;
  }

  @Override
  public Mono<String> fetchOrCreatePseudonym(String domain, String originalValue) {
    log.trace("Fetching pseudonym from Entici: domain={}, originalValue={}", domain, originalValue);
    return enticiClient
        .fetchOrCreatePseudonym(domain, originalValue)
        .doOnSuccess(p -> log.trace("Entici returned pseudonym for {}", originalValue));
  }

  @Override
  public Mono<Map<String, String>> fetchOrCreatePseudonyms(
      String domain, Set<String> originalValues) {
    log.trace("Fetching {} pseudonyms from Entici: domain={}", originalValues.size(), domain);
    return enticiClient
        .fetchOrCreatePseudonyms(domain, originalValues)
        .doOnSuccess(m -> log.trace("Entici returned {} pseudonyms", m.size()));
  }

  @Override
  public String getBackendType() {
    return BACKEND_TYPE;
  }
}
