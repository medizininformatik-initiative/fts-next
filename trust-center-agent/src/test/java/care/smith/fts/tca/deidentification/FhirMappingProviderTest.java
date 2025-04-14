package care.smith.fts.tca.deidentification;

import static care.smith.fts.test.FhirGenerators.fromList;
import static care.smith.fts.test.MockServerUtil.APPLICATION_FHIR_JSON;
import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static care.smith.fts.util.fhir.FhirUtils.fhirResourceToString;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.DateShiftPreserve;
import care.smith.fts.tca.deidentification.configuration.TransportMappingConfiguration;
import care.smith.fts.test.FhirGenerators;
import care.smith.fts.test.TestWebClientFactory;
import care.smith.fts.util.error.fhir.FhirException;
import care.smith.fts.util.tca.TCADomains;
import care.smith.fts.util.tca.TransportMappingRequest;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ContentTypes;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RMapCacheReactive;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.client.RedisTimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@SpringBootTest
@WireMockTest
@ExtendWith(MockitoExtension.class)
@Import(TestWebClientFactory.class)
class FhirMappingProviderTest {

  private static final TCADomains DEFAULT_DOMAINS = new TCADomains("domain", "domain", "domain");
  private static final TransportMappingRequest DEFAULT_REQUEST =
      new TransportMappingRequest(
          "id1", Set.of("id1"), DEFAULT_DOMAINS, Duration.ofDays(14), DateShiftPreserve.NONE);

  @Autowired WebClient.Builder httpClientBuilder;
  @MockitoBean RedissonClient redisClient;
  @Mock RedissonReactiveClient redis;
  @Mock RMapCacheReactive<Object, Object> mapCache;
  @Autowired TransportMappingConfiguration transportMappingConfiguration;
  @Autowired MeterRegistry meterRegistry;

  private FhirMappingProvider mappingProvider;
  private WireMock wireMock;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntime) {
    var address = wireMockRuntime.getHttpBaseUrl();
    wireMock = wireMockRuntime.getWireMock();

    given(redisClient.reactive()).willReturn(redis);

    var gpasClient = new GpasClient(httpClientBuilder.baseUrl(address).build(), meterRegistry);

    mappingProvider =
        new FhirMappingProvider(
            gpasClient,
            redisClient,
            transportMappingConfiguration,
            meterRegistry,
            new RandomStringGenerator(new Random(0)));
  }

  @Test
  void generateTransportMapping() throws IOException {
    var fhirGenerator =
        FhirGenerators.gpasGetOrCreateResponse(
            fromList(List.of("id1", "Salt_id1", "PT336H_id1")),
            fromList(List.of("469680023", "123", "12345")));

    List.of("id1", "Salt_id1", "PT336H_id1")
        .forEach(
            key ->
                wireMock.register(
                    post(urlEqualTo("/$pseudonymizeAllowCreate"))
                        .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON))
                        .withRequestBody(
                            equalToJson(
                                """
                                { "resourceType": "Parameters",
                                  "parameter": [
                                    {"name": "target", "valueString": "domain"},
                                    {"name": "original", "valueString": "%s"}]}
                                """
                                    .formatted(key),
                                true,
                                true))
                        .willReturn(fhirResponse(fhirGenerator.generateString()))));

    given(redis.getMapCache(anyString())).willReturn(mapCache);
    given(mapCache.expire(Duration.ofMinutes(10))).willReturn(Mono.just(false));
    given(mapCache.putAll(anyMap())).willReturn(Mono.empty());

    var ids = Set.of("Patient.id1", "identifier.id1");
    var mapName = "wSUYQUR3Y";
    var request =
        new TransportMappingRequest(
            "id1", ids, DEFAULT_DOMAINS, Duration.ofDays(14), DateShiftPreserve.NONE);
    create(mappingProvider.generateTransportMapping(request))
        .assertNext(
            r -> {
              assertThat(r.transferId()).isEqualTo(mapName);
              assertThat(r.transportMapping().keySet()).isEqualTo(ids);
              assertThat(r.transportMapping().values())
                  .containsExactlyInAnyOrder("MLfKoQoSv", "HFbzdJo87");
              assertThat(r.dateShiftValue()).isLessThanOrEqualTo(Duration.ofMillis(606851642L));
            })
        .verifyComplete();
  }

  @Test
  void generateTransportMappingWhenRedisDown() {
    given(redis.getMapCache(anyString())).willThrow(new RedisTimeoutException("timeout"));
    assertThrows(
        RedisTimeoutException.class,
        () -> mappingProvider.generateTransportMapping(DEFAULT_REQUEST));
  }

  @Test
  void fetchResearchMapping() {
    given(redis.getMapCache(anyString())).willReturn(mapCache);
    given(mapCache.readAllMap())
        .willReturn(
            Mono.just(Map.of("id1", "123456789", "id2", "987654321", "dateShiftMillis", "12345")));
    create(mappingProvider.fetchResearchMapping("transferId"))
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
  void fetchResearchMappingWhenRedisDown() {
    given(redis.getMapCache(anyString())).willThrow(new RedisTimeoutException("timeout"));
    create(mappingProvider.fetchResearchMapping("transferId"))
        .expectError(RedisTimeoutException.class)
        .verify();
  }

  private static CapabilityStatement gpasMockCapabilityStatement() {
    var capabilities = new CapabilityStatement();
    var rest = capabilities.addRest();
    rest.addOperation().setName("pseudonymizeAllowCreate");
    return capabilities;
  }

  @Test
  void fetchResearchMappingWithUnknownDomainException() {
    wireMock.register(
        post("/$pseudonymizeAllowCreate")
            .willReturn(
                fhirResponse(
                    """
                       {"resourceType": "OperationOutcome",
                        "issue": [{"severity": "error", "code": "processing",
                                   "diagnostics": "Unknown domain"}]}
                       """,
                    BAD_REQUEST)));
    wireMock.register(
        get(urlPathEqualTo("/metadata"))
            .withQueryParam("_elements", equalTo("rest.operation"))
            .willReturn(
                status(OK.value())
                    .withHeader(ContentTypes.CONTENT_TYPE, APPLICATION_FHIR_JSON)
                    .withBody(fhirResourceToString(gpasMockCapabilityStatement()))));

    given(redis.getMapCache(anyString())).willReturn(mapCache);
    given(mapCache.expire(Duration.ofMinutes(10))).willReturn(Mono.just(false));

    create(mappingProvider.generateTransportMapping(DEFAULT_REQUEST))
        .expectError(FhirException.class)
        .verify();
  }

  @Test
  void fetchResearchMappingWithUnknownError() {
    wireMock.register(
        post("/$pseudonymizeAllowCreate")
            .willReturn(
                fhirResponse(
                    """
{"resourceType": "OperationOutcome",
"issue": [{"severity": "error", "code": "processing",
           "diagnostics": "Unknown error"}]}
""",
                    BAD_REQUEST)));
    wireMock.register(
        get(urlPathEqualTo("/metadata"))
            .withQueryParam("_elements", equalTo("rest.operation"))
            .willReturn(
                status(OK.value())
                    .withHeader(ContentTypes.CONTENT_TYPE, APPLICATION_FHIR_JSON)
                    .withBody(fhirResourceToString(gpasMockCapabilityStatement()))));

    given(redis.getMapCache(anyString())).willReturn(mapCache);
    given(mapCache.expire(Duration.ofMinutes(10))).willReturn(Mono.just(false));

    create(mappingProvider.generateTransportMapping(DEFAULT_REQUEST))
        .expectError(FhirException.class)
        .verify();
  }

  @Test
  void fetchResearchMappingWrongDateShiftValue() {
    given(redis.getMapCache(anyString())).willReturn(mapCache);
    given(mapCache.readAllMap())
        .willReturn(
            Mono.just(Map.of("id1", "123456789", "id2", "987654321", "dateShiftMillis", "nan")));
    create(mappingProvider.fetchResearchMapping("transferId"))
        .expectErrorMessage("Invalid dateShiftMillis value.")
        .verify();
  }

  @AfterEach
  void tearDown() {
    wireMock.resetMappings();
  }
}
