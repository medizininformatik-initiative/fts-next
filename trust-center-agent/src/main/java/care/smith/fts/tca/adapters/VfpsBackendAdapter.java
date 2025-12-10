package care.smith.fts.tca.adapters;

import java.util.Map;
import java.util.Set;
import reactor.core.publisher.Mono;

/**
 * Backend adapter for Vfps (Vfps Pseudonymization Service).
 *
 * <p>This adapter communicates with an external Vfps service to generate pseudonyms. It implements
 * the {@link PseudonymBackendAdapter} interface for use with the FHIR Pseudonymizer integration.
 *
 * <p><b>Note:</b> This is a placeholder implementation. Full Vfps protocol support will be added
 * when a Vfps service is available for testing.
 */
public class VfpsBackendAdapter implements PseudonymBackendAdapter {

  private static final String BACKEND_TYPE = "vfps";

  @Override
  public Mono<String> fetchOrCreatePseudonym(String domain, String originalValue) {
    return Mono.error(
        new UnsupportedOperationException(
            "Vfps backend adapter is not yet implemented. "
                + "Configure 'gpas' as the backend type or contribute an implementation."));
  }

  @Override
  public Mono<Map<String, String>> fetchOrCreatePseudonyms(
      String domain, Set<String> originalValues) {
    return Mono.error(
        new UnsupportedOperationException(
            "Vfps backend adapter is not yet implemented. "
                + "Configure 'gpas' as the backend type or contribute an implementation."));
  }

  @Override
  public String getBackendType() {
    return BACKEND_TYPE;
  }
}
