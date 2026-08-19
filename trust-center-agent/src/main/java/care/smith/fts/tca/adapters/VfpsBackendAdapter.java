package care.smith.fts.tca.adapters;

import care.smith.fts.tca.deidentification.VfpsClient;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Backend adapter for Vfps (Very Fast Pseudonym Service).
 *
 * <p>This adapter communicates with an external Vfps service to generate pseudonyms. It implements
 * the {@link PseudonymBackendAdapter} interface for use with the FHIR Pseudonymizer integration.
 */
@Slf4j
public class VfpsBackendAdapter implements PseudonymBackendAdapter {

  private static final String BACKEND_TYPE = "vfps";
  private final VfpsClient vfpsClient;

  public VfpsBackendAdapter(VfpsClient vfpsClient) {
    this.vfpsClient = vfpsClient;
  }

  @Override
  public Mono<String> fetchOrCreatePseudonym(String domain, String originalValue) {
    log.trace("Fetching pseudonym from Vfps: domain={}, originalValue={}", domain, originalValue);
    return vfpsClient
        .fetchOrCreatePseudonym(domain, originalValue)
        .doOnSuccess(p -> log.trace("Vfps returned pseudonym for {}", originalValue));
  }

  @Override
  public Mono<Map<String, String>> fetchOrCreatePseudonyms(
      String domain, Set<String> originalValues) {
    log.trace("Fetching {} pseudonyms from Vfps: domain={}", originalValues.size(), domain);
    return vfpsClient
        .fetchOrCreatePseudonyms(domain, originalValues)
        .doOnSuccess(m -> log.trace("Vfps returned {} pseudonyms", m.size()));
  }

  @Override
  public String getBackendType() {
    return BACKEND_TYPE;
  }
}
