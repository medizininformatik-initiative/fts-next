package care.smith.fts.tca.deidentification;

import care.smith.fts.util.tca.PseudonymizeResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import reactor.core.publisher.Mono;

public interface PseudonymProvider {

  /**
   * @param ids to transport ids
   * @param domain the domain
   * @param maxDateShift
   * @return the <code>PseudonymResponse</code>
   */
  Mono<PseudonymizeResponse> retrieveTransportIds(
      String patientId, Set<String> ids, String domain, Duration maxDateShift);

  /**
   * Retrieves the mapping of <code>transportId</code> to <code>secureId</code> using the mappings
   * name.
   *
   * @param tIDMapName the transport id map name
   * @return the mapped tid:sid
   */
  Mono<Map<String, String>> fetchPseudonymizedIds(String tIDMapName);
}
