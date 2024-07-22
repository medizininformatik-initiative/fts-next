package care.smith.fts.tca.deidentification;

import java.util.Map;
import java.util.Set;
import reactor.core.publisher.Mono;

public interface PseudonymProvider {

  /**
   * @param ids to transport ids
   * @param domain the domain
   * @return the <code>PseudonymResponse</code>
   */
  Mono<Map<String, String>> retrieveTransportIds(Set<String> ids, String domain);

  /**
   * Retrieves the mapping of <code>transportId</code> to <code>secureId</code>
   *
   * @param ids the transport ids
   * @return the mapped tid:sid
   */
  Mono<Map<String, String>> fetchPseudonymizedIds(Set<String> ids);
}
