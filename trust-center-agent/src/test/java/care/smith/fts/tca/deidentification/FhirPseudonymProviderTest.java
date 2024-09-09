package care.smith.fts.tca.deidentification;

import static care.smith.fts.test.FhirGenerators.fromList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockserver.matchers.MatchType.ONLY_MATCHING_FIELDS;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static reactor.test.StepVerifier.create;

import care.smith.fts.tca.deidentification.configuration.PseudonymizationConfiguration;
import care.smith.fts.test.FhirGenerators;
import care.smith.fts.util.MediaTypes;
import care.smith.fts.test.TestWebClientAuth;
import care.smith.fts.util.error.UnknownDomainException;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
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
import org.redisson.api.RMapCacheReactive;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.client.RedisTimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@SpringBootTest
@ExtendWith(MockServerExtension.class)
@ExtendWith(MockitoExtension.class)
@Import(TestWebClientAuth.class)
class FhirPseudonymProviderTest {
  private static final Long SEED = 101620L;

  @Autowired WebClient.Builder httpClientBuilder;
  @MockBean RedissonClient redisClient;
  @Mock RedissonReactiveClient redis;
  @Mock RMapCacheReactive<Object, Object> mapCache;
  @Autowired PseudonymizationConfiguration pseudonymizationConfiguration;
  @Autowired MeterRegistry meterRegistry;

  private FhirPseudonymProvider pseudonymProvider;

  @BeforeEach
  void setUp(MockServerClient mockServer) {
    var address = "http://localhost:%d".formatted(mockServer.getPort());

    given(redisClient.reactive()).willReturn(redis);

    pseudonymProvider =
        new FhirPseudonymProvider(
            httpClientBuilder.baseUrl(address).build(),
            redisClient,
            pseudonymizationConfiguration,
            new Random(SEED),
            meterRegistry);
  }

  @Test
  void retrieveTransportIds(MockServerClient mockServer) throws IOException {
    var fhirGenerator =
        FhirGenerators.gpasGetOrCreateResponse(
            fromList(List.of("id1", "Salt_id1")), fromList(List.of("469680023", "123")));

    mockServer
        .when(
            request()
                .withMethod("POST")
                .withPath("/$pseudonymizeAllowCreate")
                .withBody(
                    json(
                        """
                                  { "resourceType": "Parameters",
                                    "parameter": [
                                      {"name": "target", "valueString": "domain"},
                                      {"name": "original", "valueString": "id1"},
                                      {"name": "original", "valueString": "Salt_id1"}]}
                                  """,
                        ONLY_MATCHING_FIELDS)))
        .respond(
            response()
                .withBody(
                    fhirGenerator.generateString(), MediaType.create("application", "fhir+json")));

    given(redis.getMapCache(anyString())).willReturn(mapCache);
    given(mapCache.expire(Duration.ofSeconds(1000))).willReturn(Mono.just(false));
    given(mapCache.putAll(anyMap())).willReturn(Mono.empty());

    var ids = Set.of("Patient.id1", "identifier.id1");
    var mapName = "Bo1z3Z87i";
    create(pseudonymProvider.retrieveTransportIds("id1", ids, "domain"))
        .assertNext(
            t2 -> {
              assertThat(t2.getT1()).isEqualTo(mapName);
              assertThat(t2.getT2().keySet()).isEqualTo(ids);
              assertThat(t2.getT2().values()).containsExactlyInAnyOrder("xLCUONMhJ", "S3DWTXcF_");
            })
        .verifyComplete();
  }

  @Test
  void retrieveTransportIdsWhenRedisDown() {
    given(redis.getMapCache(anyString())).willThrow(new RedisTimeoutException("timeout"));
    assertThrows(
        RedisTimeoutException.class,
        () -> pseudonymProvider.retrieveTransportIds("id1", Set.of("id1"), "domain"));
  }

  @Test
  void fetchPseudonymIDs() {
    given(redis.getMapCache(anyString())).willReturn(mapCache);
    given(mapCache.readAllMap())
        .willReturn(Mono.just(Map.of("id1", "123456789", "id2", "987654321")));
    create(pseudonymProvider.fetchPseudonymizedIds("tIDMapName"))
        .expectNextMatches(
            m ->
                m.containsKey("id1")
                    && m.containsKey("id2")
                    && m.containsValue("123456789")
                    && m.containsValue("987654321"))
        .verifyComplete();
  }

  @Test
  void fetchPseudonymIDsWhenRedisDown() {
    given(redis.getMapCache(anyString())).willThrow(new RedisTimeoutException("timeout"));
    create(pseudonymProvider.fetchPseudonymizedIds("tIDMapName"))
        .expectError(RedisTimeoutException.class)
        .verify();
  }

  @Test
  void fetchPseudonymIDsWithUnknownDomainException(MockServerClient mockServer) {
    mockServer
        .when(request().withMethod("POST").withPath("/$pseudonymizeAllowCreate"))
        .respond(
            response()
                .withStatusCode(400)
                .withContentType(MediaType.parse(MediaTypes.APPLICATION_FHIR_JSON_VALUE))
                .withBody(
                    "{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\",\"code\":\"processing\",\"diagnostics\":\"Unknown domain\"}]}"));

    given(redis.getMapCache(anyString())).willReturn(mapCache);
    given(mapCache.expire(Duration.ofSeconds(1000))).willReturn(Mono.just(false));

    Set<String> ids = Set.of("id1");
    create(pseudonymProvider.retrieveTransportIds("id1", ids, "domain"))
        .expectError(UnknownDomainException.class)
        .verify();
  }

  @Test
  void fetchPseudonymIDsWithUnknownError(MockServerClient mockServer) {
    mockServer
        .when(request().withMethod("POST").withPath("/$pseudonymizeAllowCreate"))
        .respond(
            response()
                .withStatusCode(400)
                .withContentType(MediaType.parse(MediaTypes.APPLICATION_FHIR_JSON_VALUE))
                .withBody(
                    "{\"resourceType\":\"OperationOutcome\",\"issue\":[{\"severity\":\"error\",\"code\":\"processing\",\"diagnostics\":\"Unknown error\"}]}"));

    given(redis.getMapCache(anyString())).willReturn(mapCache);
    given(mapCache.expire(Duration.ofSeconds(1000))).willReturn(Mono.just(false));

    Set<String> ids = Set.of("id1");
    create(pseudonymProvider.retrieveTransportIds("id1", ids, "domain"))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @AfterEach
  void tearDown(MockServerClient mockServer) {
    mockServer.reset();
  }
}
