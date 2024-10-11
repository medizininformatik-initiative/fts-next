package care.smith.fts.tca.deidentification;

import static care.smith.fts.test.FhirGenerators.fromList;
import static care.smith.fts.test.MockServerUtil.APPLICATION_FHIR_JSON;
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
import care.smith.fts.test.TestWebClientFactory;
import care.smith.fts.util.MediaTypes;
import care.smith.fts.util.error.UnknownDomainException;
import care.smith.fts.util.tca.TCADomains;
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
@Import(TestWebClientFactory.class)
class FhirPseudonymProviderTest {

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

    var gpasClient = new GpasClient(httpClientBuilder.baseUrl(address).build(), meterRegistry);

    pseudonymProvider =
        new FhirPseudonymProvider(
            gpasClient,
            redisClient,
            pseudonymizationConfiguration,
            meterRegistry,
            new RandomStringGenerator(new Random(0)));
  }

  @Test
  void retrieveTransportIds(MockServerClient mockServer) throws IOException {
    var fhirGenerator =
        FhirGenerators.gpasGetOrCreateResponse(
            fromList(List.of("id1", "Salt_id1", "PT336H_id1")),
            fromList(List.of("469680023", "123", "12345")));

    var request = request().withMethod("POST").withPath("/$pseudonymizeAllowCreate");
    List.of("id1", "Salt_id1", "PT336H_id1")
        .forEach(
            key ->
                mockServer
                    .when(
                        request
                            .withBody(
                                json(
                                    """
                                  { "resourceType": "Parameters",
                                    "parameter": [
                                      {"name": "target", "valueString": "domain"},
                                      {"name": "original", "valueString": "%s"}]}
                                  """
                                        .formatted(key),
                                    ONLY_MATCHING_FIELDS))
                            .withContentType(APPLICATION_FHIR_JSON))
                    .respond(
                        response()
                            .withBody(
                                fhirGenerator.generateString(),
                                MediaType.create("application", "fhir+json"))));

    given(redis.getMapCache(anyString())).willReturn(mapCache);
    given(mapCache.expire(Duration.ofSeconds(1000))).willReturn(Mono.just(false));
    given(mapCache.putAll(anyMap())).willReturn(Mono.empty());

    var ids = Set.of("Patient.id1", "identifier.id1");
    var mapName = "wSUYQUR3Y";
    create(
            pseudonymProvider.retrieveTransportIds(
                "id1", ids, new TCADomains("domain", "domain", "domain"), Duration.ofDays(14)))
        .assertNext(
            r -> {
              assertThat(r.tIDMapName()).isEqualTo(mapName);
              assertThat(r.originalToTransportIDMap().keySet()).isEqualTo(ids);
              assertThat(r.originalToTransportIDMap().values())
                  .containsExactlyInAnyOrder("MLfKoQoSv", "HFbzdJo87");
              assertThat(r.dateShiftValue()).isLessThanOrEqualTo(Duration.ofMillis(606851642L));
            })
        .verifyComplete();
  }

  @Test
  void retrieveTransportIdsWhenRedisDown() {
    given(redis.getMapCache(anyString())).willThrow(new RedisTimeoutException("timeout"));
    assertThrows(
        RedisTimeoutException.class,
        () ->
            pseudonymProvider.retrieveTransportIds(
                "id1",
                Set.of("id1"),
                new TCADomains("domain", "domain", "domain"),
                Duration.ofDays(14)));
  }

  @Test
  void fetchPseudonymIDs() {
    given(redis.getMapCache(anyString())).willReturn(mapCache);
    given(mapCache.readAllMap())
        .willReturn(
            Mono.just(Map.of("id1", "123456789", "id2", "987654321", "dateShiftMillis", "12345")));
    create(pseudonymProvider.resolveTransportData("tIDMapName"))
        .assertNext(
            m -> {
              assertThat(m.tidPidMap().keySet()).containsExactlyInAnyOrder("id1", "id2");
              assertThat(m.tidPidMap().values())
                  .containsExactlyInAnyOrder("123456789", "987654321");
              assertThat(m.dateShiftBy()).isEqualTo(Duration.ofMillis(12345));
            })
        .verifyComplete();
  }

  @Test
  void fetchPseudonymIDsWhenRedisDown() {
    given(redis.getMapCache(anyString())).willThrow(new RedisTimeoutException("timeout"));
    create(pseudonymProvider.resolveTransportData("tIDMapName"))
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
    create(
            pseudonymProvider.retrieveTransportIds(
                "id1", ids, new TCADomains("domain", "domain", "domain"), Duration.ofDays(14)))
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
    create(
            pseudonymProvider.retrieveTransportIds(
                "id1", ids, new TCADomains("domain", "domain", "domain"), Duration.ofDays(14)))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @AfterEach
  void tearDown(MockServerClient mockServer) {
    mockServer.reset();
  }
}
