package care.smith.fts.tca.deidentification;

import care.smith.fts.util.tca.ResearchMappingResponse;
import care.smith.fts.util.tca.TransportMappingRequest;
import care.smith.fts.util.tca.TransportMappingResponse;
import reactor.core.publisher.Mono;

public interface MappingProvider {

  /**
   * @param r the transport mapping request
   * @return the <code>PseudonymResponse</code>
   */
  Mono<TransportMappingResponse> generateTransportMapping(TransportMappingRequest r);

  /**
   * Retrieves the mapping of <code>transportId</code> to <code>secureId</code> using the mappings
   * name.
   *
   * @param transferId unique ID for a transfer process to identify mapping
   * @return the mapped tid:sid
   */
  Mono<ResearchMappingResponse> fetchResearchMapping(String transferId);
}
