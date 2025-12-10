package care.smith.fts.tca.adapters;

import java.util.Map;
import java.util.Set;
import reactor.core.publisher.Mono;

/**
 * Backend adapter for entici pseudonymization service.
 *
 * <p>This adapter communicates with an external entici service to generate pseudonyms. It
 * implements the {@link PseudonymBackendAdapter} interface for use with the FHIR Pseudonymizer
 * integration.
 *
 * <p><b>Note:</b> This is a placeholder implementation. Full entici protocol support will be added
 * when an entici service is available for testing.
 */
public class EnticiBackendAdapter implements PseudonymBackendAdapter {

  private static final String BACKEND_TYPE = "entici";

  @Override
  public Mono<String> fetchOrCreatePseudonym(String domain, String originalValue) {
    return Mono.error(
        new UnsupportedOperationException(
            "Entici backend adapter is not yet implemented. "
                + "Configure 'gpas' as the backend type or contribute an implementation."));
  }

  @Override
  public Mono<Map<String, String>> fetchOrCreatePseudonyms(
      String domain, Set<String> originalValues) {
    return Mono.error(
        new UnsupportedOperationException(
            "Entici backend adapter is not yet implemented. "
                + "Configure 'gpas' as the backend type or contribute an implementation."));
  }

  @Override
  public String getBackendType() {
    return BACKEND_TYPE;
  }
}
