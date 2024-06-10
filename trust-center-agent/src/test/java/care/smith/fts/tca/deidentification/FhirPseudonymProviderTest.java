package care.smith.fts.tca.deidentification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import care.smith.fts.tca.deidentification.configuration.PseudonymizationConfiguration;
import care.smith.fts.test.FhirGenerator;
import care.smith.fts.util.tca.TransportIdsRequest;
import care.smith.fts.util.tca.PseudonymizedIDs;
import care.smith.fts.util.tca.TransportIDs;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

@Slf4j
@SpringBootTest
@ExtendWith(MockitoExtension.class)
class FhirPseudonymProviderTest {
  @Mock CloseableHttpClient httpClient;
  @Mock Jedis jedis;
  @Autowired ObjectMapper objectMapper;
  @Autowired PseudonymizationConfiguration pseudonymizationConfiguration;

  private FhirPseudonymProvider pseudonymProvider;

  @BeforeEach
  void setUp() {
    JedisPool jedisPool = Mockito.mock(JedisPool.class);
    given(jedisPool.getResource()).willReturn(jedis);
    pseudonymProvider =
        new FhirPseudonymProvider(
            httpClient, objectMapper, jedisPool, pseudonymizationConfiguration);
  }

  @Test
  void retrieveTransportIds() throws IOException {
    FhirGenerator fhirGenerator =
        new FhirGenerator("deidentification/gpas-get-or-create-response.json");
    fhirGenerator.replaceTemplateFieldWith("$ORIGINAL", new FhirGenerator.Fixed("id1"));
    fhirGenerator.replaceTemplateFieldWith("$PSEUDONYM", new FhirGenerator.Fixed("469680023"));

    given(httpClient.execute(any(HttpPost.class), any(HttpClientResponseHandler.class)))
        .willReturn(fhirGenerator.generateInputStream());

    given(jedis.set(anyString(), anyString(), any(SetParams.class))).willReturn("OK");
    // In retrieveTransportIds(), the first jedis.get() checks whether the ID exists
    // already. By returning null every other call, we simulate that it is a unique ID.
    given(jedis.get(anyString())).willReturn(null, "469680023");

    TransportIdsRequest transportIdsRequest = new TransportIdsRequest();
    transportIdsRequest.setIds(Set.of("id1"));
    TransportIDs pseudonyms =
        pseudonymProvider.retrieveTransportIds(
            transportIdsRequest.getIds(), transportIdsRequest.getDomain());

    String id1 = pseudonyms.get("id1");
    log.info(Arrays.toString(id1.getBytes(StandardCharsets.UTF_8)));
    assertThat(id1).isNotNull();
  }

  @Test
  void retrievePseudonymIDs() {
    given(jedis.getDel(anyString())).willReturn("123456789", "987654321");
    TransportIdsRequest transportIdsRequest = new TransportIdsRequest();
    transportIdsRequest.setIds(Set.of("id1", "id2"));
    PseudonymizedIDs pseudonymizedIDs =
        pseudonymProvider.fetchPseudonymizedIds(transportIdsRequest);
    assertThat(pseudonymizedIDs.keySet()).containsExactlyInAnyOrder("id1", "id2");
    assertThat(pseudonymizedIDs.values()).containsExactlyInAnyOrder("123456789", "987654321");
  }
}
