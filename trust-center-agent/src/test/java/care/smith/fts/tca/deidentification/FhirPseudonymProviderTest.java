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
import care.smith.fts.test.FhirGenerators;
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
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.model.MediaType;
import org.redisson.api.RBucketReactive;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@SpringBootTest
@ExtendWith(MockServerExtension.class)
@ExtendWith(MockitoExtension.class)
class FhirPseudonymProviderTest {
  private static final Long SEED = 101620L;

  @Autowired WebClient.Builder httpClientBuilder;
  @MockBean RedissonClient redisClient;
  @Mock RedissonReactiveClient redis;
  @Mock RBucketReactive<Object> bucket;
  @Autowired PseudonymizationConfiguration pseudonymizationConfiguration;

  private FhirPseudonymProvider pseudonymProvider;

  @BeforeEach
  void setUp(MockServerClient mockServer) {
    var address = "http://localhost:%d".formatted(mockServer.getPort());

    given(redisClient.reactive()).willReturn(redis);
    given(redis.getBucket(anyString())).willReturn(bucket);

    pseudonymProvider =
        new FhirPseudonymProvider(
            httpClientBuilder.baseUrl(address).build(),
            redisClient,
            pseudonymizationConfiguration,
            new Random(SEED));
  }

  @Test
  void retrieveTransportIds(MockServerClient mockServer) throws IOException {
    var fhirGenerator = FhirGenerators.gpasGetOrCreateResponse(() -> "id1", () -> "469680023");

    mockServer
        .when(
            request()
                .withMethod("POST")
                .withPath("/$pseudonymizeAllowCreate")
                .withBody(
                    json(
                        """
                                  { "resourceType": "Parameters", "parameter": [
                                    {"name": "target", "valueString": "domain"}, {"name":
   "original", "valueString": "id1"}]}
                                  """,
                        ONLY_MATCHING_FIELDS)))
        .respond(
            response()
                .withBody(
                    fhirGenerator.generateString(), MediaType.create("application", "fhir+json")));

    given(bucket.setIfAbsent(anyString(), any())).willReturn(Mono.just(true));
    given(bucket.get()).willReturn(Mono.empty());

    Map<String, String> idMap = Map.of("id1", "Bo1z3Z87i");
    create(pseudonymProvider.retrieveTransportIds(Set.of("id1"), "domain"))
        .expectNext(idMap)
        .verifyComplete();
  }

  @Test
  void retrievePseudonymIDs() {
    given(bucket.get()).willReturn(Mono.just("123456789"), Mono.just("987654321"));
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
