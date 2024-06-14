package care.smith.fts.tca.deidentification;

import care.smith.fts.tca.deidentification.configuration.PseudonymizationConfiguration;
import care.smith.fts.util.tca.IDMap;
import care.smith.fts.util.tca.TransportIdsRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.entity.StringEntity;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

@Slf4j
public class FhirPseudonymProvider implements PseudonymProvider {
  private final CloseableHttpClient httpClient;
  private final PseudonymizationConfiguration configuration;
  private final ObjectMapper objectMapper;
  private final JedisPool jedisPool;
  private final SecureRandom secureRandom = new SecureRandom();

  public FhirPseudonymProvider(
      CloseableHttpClient httpClient,
      ObjectMapper objectMapper,
      JedisPool jedisPool,
      PseudonymizationConfiguration configuration) {
    this.httpClient = httpClient;
    this.configuration = configuration;
    this.objectMapper = objectMapper;
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
  public IDMap retrieveTransportIds(Set<String> ids, String domain) throws IOException {
    var idPseudonyms = fetchOrCreatePseudonyms(domain, ids);
    IDMap transportIds = new IDMap();
    ids.forEach(id -> transportIds.put(id, getUniqueTransportId()));

    try (Jedis jedis = jedisPool.getResource()) {
      ids.forEach(
          id -> {
            var transportId = transportIds.get(id);
            var kid = "tid:" + transportId;
            String pid = idPseudonyms.get(id);
            if (jedis.set(kid, pid, new SetParams().nx()).equals("OK")) {
              jedis.expire(kid, configuration.getTransportIdTTLinSeconds());
            } else {
              throw new RuntimeException("Could not put {kid}-{pid} into key-value-store");
            }
          });
    }

    return transportIds;
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

  private IDMap fetchOrCreatePseudonyms(String domain, Set<String> ids) throws IOException {
    var response = httpClient.execute(httpPost(domain, ids), r -> r.getEntity().getContent());
    return objectMapper.readValue(response, GpasParameterResponse.class).getMappedID();
  }

  private HttpPost httpPost(String domain, Set<String> ids) {
    HttpPost post = new HttpPost("/$pseudonymizeAllowCreate");
    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder
        .append(
            "{\"resourceType\": \"Parameters\", \"parameter\": [{\"name\": \"target\", \"valueString\": \"")
        .append(domain)
        .append("\"}");

    ids.forEach(
        id -> {
          stringBuilder.append(", {\"name\": \"original\", \"valueString\": \"");
          stringBuilder.append(id);
          stringBuilder.append("\"}");
        });

    post.setEntity(new StringEntity(stringBuilder.toString()));
    post.setHeader("Content-Type", "application/json");
    return post;
  }

  @Override
  public IDMap fetchPseudonymizedIds(TransportIdsRequest transportIdsRequest) {
    IDMap pseudonyms = new IDMap();
    try (Jedis jedis = jedisPool.getResource()) {
      Set<String> ids = transportIdsRequest.getIds();
      ids.forEach(
          id -> {
            var pseudonymId = jedis.get(id);
            pseudonyms.put(id, pseudonymId);
          });
    }
    return pseudonyms;
  }

  @Override
  public void deleteTransportId(TransportIdsRequest transportIdsRequest) {
    try (Jedis jedis = jedisPool.getResource()) {
      Set<String> ids = transportIdsRequest.getIds();
      jedis.del(ids.toArray(new String[0]));
    }
  }
}
