package care.smith.fts.tca.deidentification;

import care.smith.fts.tca.deidentification.configuration.PseudonymizationConfiguration;
import care.smith.fts.util.tca.IDMap;
import care.smith.fts.util.tca.TransportIdsRequest;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

@Slf4j
public class FhirPseudonymProvider implements PseudonymProvider {
  private final WebClient httpClient;
  private final PseudonymizationConfiguration configuration;
  private final JedisPool jedisPool;
  private final SecureRandom secureRandom = new SecureRandom();

  public FhirPseudonymProvider(
      WebClient httpClient, JedisPool jedisPool, PseudonymizationConfiguration configuration) {
    this.httpClient = httpClient;
    this.configuration = configuration;
    this.jedisPool = jedisPool;
  }

  /**
   * For all provided IDs fetch the id:pid pairs from gPAS. Then create TransportIDs (id:tid pairs).
   * Store tid:pid in the key-value-store.
   *
   * @param ids the IDs to pseudonymize
   * @param domain the domain used in gPAS
   * @return the TransportIDs
   */
  @Override
  public Mono<IDMap> retrieveTransportIds(Set<String> ids, String domain) {
    IDMap transportIds = new IDMap();
    ids.forEach(id -> transportIds.put(id, "123456789")); // getUniqueTransportId()));

    return fetchOrCreatePseudonyms(domain, ids)
        .map(
            idPseudonyms -> {
              try (Jedis jedis = jedisPool.getResource()) {
                ids.forEach(
                    id -> {
                      var transportId = transportIds.get(id);
                      var kid = "tid:" + transportId;
                      String pid = idPseudonyms.get(id);
                      if (jedis.set(kid, pid, new SetParams().nx()).equals("OK")) {
                        jedis.expire(kid, configuration.getTransportIdTTLinSeconds());
                      } else {
                        throw new RuntimeException(
                            "Could not put {kid}-{pid} into key-value-store");
                      }
                    });
              }

              return transportIds;
            });
  }

  /** Generate a random transport ID and make sure it does not yet exist in the key-value-store. */
  private String getUniqueTransportId() {
    byte[] bytes = new byte[9];
    secureRandom.nextBytes(bytes);
    try (Jedis jedis = jedisPool.getResource()) {
      if (jedis.get("tid:" + Arrays.toString(bytes)) != null) {
        return getUniqueTransportId();
      }
    }
    return Arrays.toString(bytes);
  }

  private Mono<IDMap> fetchOrCreatePseudonyms(String domain, Set<String> ids) {
    var idParams =
        Stream.concat(
            Stream.of(Map.of("name", "target", "valueString", domain)),
            ids.stream().map(id -> Map.of("name", "original", "valueString", id)));
    var params = Map.of("resourceType", "Parameters", "parameter", idParams.toList());

    return httpClient
        .post()
        .uri("/$pseudonymizeAllowCreate")
        .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
        .bodyValue(params)
        .retrieve()
        .bodyToMono(GpasParameterResponse.class)
        .map(GpasParameterResponse::getMappedID);
  }

  @Override
  public Mono<IDMap> fetchPseudonymizedIds(TransportIdsRequest transportIdsRequest) {
    IDMap pseudonyms = new IDMap();
    try (Jedis jedis = jedisPool.getResource()) {
      Set<String> ids = transportIdsRequest.getIds();
      ids.forEach(
          id -> {
            var pseudonymId = jedis.get(id);
            pseudonyms.put(id, pseudonymId);
          });
    }
    return Mono.just(pseudonyms);
  }

  @Override
  public Mono<Void> deleteTransportId(TransportIdsRequest transportIdsRequest) {
    try (Jedis jedis = jedisPool.getResource()) {
      Set<String> ids = transportIdsRequest.getIds();
      jedis.del(ids.toArray(new String[0]));
    }
    return Mono.empty();
  }
}
