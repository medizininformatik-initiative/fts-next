package care.smith.fts.tca.deidentification;

import care.smith.fts.util.tca.PseudonymizeResponse;
import care.smith.fts.util.tca.ResolveResponse;
import care.smith.fts.util.tca.TCADomains;
import java.time.Duration;
import java.util.Set;
import reactor.core.publisher.Mono;

public interface PseudonymProvider {

  /**
   * @param ids to transport ids
   * @param tcaDomains
   * @param maxDateShift
   * @return the <code>PseudonymResponse</code>
   */
  Mono<PseudonymizeResponse> retrieveTransportIds(
      String patientId, Set<String> ids, TCADomains tcaDomains, Duration maxDateShift);

  /**
   * Retrieves the mapping of <code>transportId</code> to <code>secureId</code> using the mappings
   * name.
   *
   * @param tIDMapName the transport id map name
   * @return the mapped tid:sid
   */
  Mono<ResolveResponse> resolveTransportData(String tIDMapName);
}
