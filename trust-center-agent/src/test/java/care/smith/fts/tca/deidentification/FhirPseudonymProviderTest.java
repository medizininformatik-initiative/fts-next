package care.smith.fts.tca.deidentification;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockserver.matchers.MatchType.ONLY_MATCHING_FIELDS;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static reactor.test.StepVerifier.create;

import care.smith.fts.tca.deidentification.configuration.PseudonymizationConfiguration;
import care.smith.fts.test.FhirGenerator;
import java.io.IOException;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.model.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

@Slf4j
@SpringBootTest
@ExtendWith(MockServerExtension.class)
@ExtendWith(MockitoExtension.class)
class FhirPseudonymProviderTest {
  private static final Long SEED = 101620L;

  @Autowired WebClient.Builder httpClientBuilder;
  @Mock Jedis jedis;
  @Autowired PseudonymizationConfiguration pseudonymizationConfiguration;

  private FhirPseudonymProvider pseudonymProvider;

  @BeforeEach
  void setUp(MockServerClient mockServer) {
    var address = "http://localhost:%d".formatted(mockServer.getPort());

    JedisPool jedisPool = Mockito.mock(JedisPool.class);
    given(jedisPool.getResource()).willReturn(jedis);
    pseudonymProvider =
        new FhirPseudonymProvider(
            httpClientBuilder.baseUrl(address).build(),
            jedisPool,
            pseudonymizationConfiguration,
            new Random(SEED));
  }

  @Test
  void retrieveTransportIds(MockServerClient mockServer) throws IOException {
    FhirGenerator fhirGenerator =
        new FhirGenerator("deidentification/gpas-get-or-create-response.json");
    fhirGenerator.replaceTemplateFieldWith("$ORIGINAL", new FhirGenerator.Fixed("id1"));
    fhirGenerator.replaceTemplateFieldWith("$PSEUDONYM", new FhirGenerator.Fixed("469680023"));

    mockServer
        .when(
            request()
                .withMethod("POST")
                .withPath("/$pseudonymizeAllowCreate")
                .withBody(
                    json(
                        """
                                { "resourceType": "Parameters", "parameter": [
                                  {"name": "target", "valueString": "domain"}, {"name": "original", "valueString": "id1"}]}
                                """,
                        ONLY_MATCHING_FIELDS)))
        .respond(
            response()
                .withBody(
                    new String(fhirGenerator.generateInputStream().readAllBytes()),
                    MediaType.create("application", "fhir+json")));

    given(jedis.set(anyString(), anyString(), any(SetParams.class))).willReturn("OK");
    // In retrieveTransportIds(), the first jedis.get() checks whether the ID exists
    // already. By returning null every other call, we simulate that it is a unique ID.
    given(jedis.get(anyString())).willReturn(null, "469680023");

    Map<String, String> idMap = Map.of("id1", "Bo1z3Z87i");
    create(pseudonymProvider.retrieveTransportIds(Set.of("id1"), "domain"))
        .expectNext(idMap)
        .verifyComplete();
  }

  @Test
  void retrievePseudonymIDs() {
    given(jedis.get(anyString())).willReturn("123456789", "987654321");
    create(pseudonymProvider.fetchPseudonymizedIds(Set.of("id1", "id2")))
        .expectNextMatches(
            m ->
                m.containsKey("id1")
                    && m.containsKey("id2")
                    && m.containsValue("123456789")
                    && m.containsValue("987654321"))
        .verifyComplete();
  }

  @AfterEach
  void tearDown(MockServerClient mockServer) {
    mockServer.reset();
  }
}
