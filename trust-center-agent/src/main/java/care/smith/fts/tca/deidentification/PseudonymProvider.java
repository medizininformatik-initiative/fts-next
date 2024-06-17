package care.smith.fts.tca.deidentification;

import care.smith.fts.util.tca.IDMap;
import care.smith.fts.util.tca.TransportIdsRequest;
import java.util.Set;
import reactor.core.publisher.Mono;

public interface PseudonymProvider {

  /**
   * @param ids to transport ids
   * @param domain the domain
   * @return the <code>PseudonymResponse</code>
   */
  Mono<IDMap> retrieveTransportIds(Set<String> ids, String domain);

  /**
   * Retrieves the <code>PseudonymRequest</code>
   *
   * @param transportIdsRequest to transport ids
   * @return the <code>PseudonymResponse</code>
   */
  Mono<IDMap> fetchPseudonymizedIds(TransportIdsRequest transportIdsRequest);

  //  /**
  //   * Retrieves the <code>PseudonymRequest</code> Returns PSICs for SICs.
  //   *
  //   * @param pseudonymRequest the SICs from the process which initiated the request
  //   * @return the <code>PseudonymResponse</code>
  //   */
  //  IDMap fetchProjectPseudonymizedIds(PseudonymRequest pseudonymRequest);

  /**
   * Removes the <code>transportId</code> to <code>secureId</code> matching from the matching table.
   *
   * @param transportIdsRequest to transport ids
   * @return <code>PseudonymResponse</code> if an existing matching was removed, <code>false</code>
   *     if no matching existed.
   */
  Mono<Void> deleteTransportId(TransportIdsRequest transportIdsRequest);

  //  /**
  //   * Returns PSN for original values, that may be compound values.
  //   *
  //   * <p>This method is called via nginx by a client.
  //   *
  //   * @param pseudonymRequest the SICs, attachments (empty strings are allowed), the domain.
  //   * @return the <code>PseudonymResponse</code>
  //   */
  //  Pseudonyms getOrCreatePSNProvidingValueAndAttachment(SomePairRequest pseudonymRequest);
  //
  //  /**
  //   * Returns PSNs of the appended original values. a) Lookup of original value b) append
  // original
  //   * value
  //   *
  //   * <p>This method is called nginx by a client.
  //   *
  //   * @param pseudonymRequest the PSNs, attachments (not optional, not empty), the domain.
  //   * @return the <code>PseudonymResponse</code> @ thrown if an error occurred in this method
  // ToDo.
  //   *     or no matching could be found ToDo!!!!!!!!!!!!!!!!
  //   */
  //  Pseudonyms getOrCreatePSNProvidingPsnAndAttachment(SomePairRequest pseudonymRequest);
  //
  //  /**
  //   * Returns pseudonymReponse containing empty map.
  //   *
  //   * <p>This method is called nginx by a client.
  //   *
  //   * @param pseudonymRequest the original values, the PSNs, the domain.
  //   * @return the <code>PseudonymResponse</code>
  //   */
  //  Pseudonyms insertValuePseudonymPairsProvidingValueAndPsn(SomePairRequest pseudonymRequest);
  //
  //  /**
  //   * Returns PSN for original values, that may be compound values.
  //   *
  //   * <p>This method is called via nginx by a client.
  //   *
  //   * @param pseudonymRequest the SICs, attachments (may be empty strings), the domain.
  //   * @return the <code>PseudonymResponse</code>
  //   */
  //  Pseudonyms getPseudonymForListProvidingValueAndAttachment(SomePairRequest pseudonymRequest);
}
