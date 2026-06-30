package care.smith.fts.tca.deidentification;

import care.smith.fts.util.tca.SecureMappingResponse;
import care.smith.fts.util.tca.TransportMappingRequest;
import care.smith.fts.util.tca.TransportMappingResponse;
import care.smith.fts.util.tca.TransportMappingsRequest;
import care.smith.fts.util.tca.TransportMappingsResponse;
import reactor.core.publisher.Mono;

public interface MappingProvider {

  /**
   * @param r the transport mapping request
   * @return the <code>PseudonymResponse</code>
   */
  Mono<TransportMappingResponse> generateTransportMapping(TransportMappingRequest r);

  /**
   * Generates transport mappings for multiple patients in one request. The per-domain gPAS
   * pseudonym lookups are collapsed into a single call per domain across all patients.
   *
   * @param r the batch transport mapping request
   * @return per-patient transferIds keyed by patient identifier
   */
  Mono<TransportMappingsResponse> generateTransportMappings(TransportMappingsRequest r);

  /**
   * Retrieves the mapping of <code>transportId</code> to <code>secureId</code> using the mappings
   * name.
   *
   * @param transferId unique ID for a transfer process to identify mapping
   * @return the mapped tid:sid
   */
  Mono<SecureMappingResponse> fetchSecureMapping(String transferId);
}
