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
import static org.mockito.Mockito.mock;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.DateShiftPreserve;
import care.smith.fts.tca.deidentification.configuration.GpasDeIdentificationConfiguration;
import care.smith.fts.tca.deidentification.configuration.TransportMappingConfiguration;
import care.smith.fts.test.FhirGenerators;
import care.smith.fts.test.TestWebClientFactory;
import care.smith.fts.util.error.fhir.FhirException;
import care.smith.fts.util.tca.TcaDomains;
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
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RMapCacheReactive;
import org.redisson.api.RMapReactive;
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

  private static final TcaDomains DEFAULT_DOMAINS = new TcaDomains("domain", "domain", "domain");
  private static final TransportMappingRequest DEFAULT_REQUEST =
      new TransportMappingRequest(
          "id1",
          "patientIdentifierSystem",
          Set.of("id1"),
          DEFAULT_DOMAINS,
          Duration.ofDays(14),
          DateShiftPreserve.NONE);

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

    var gpasConfig = new GpasDeIdentificationConfiguration();
    var gpasClient =
        new GpasClient(httpClientBuilder.baseUrl(address).build(), meterRegistry, gpasConfig);

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

    var ids = Set.of("Patient.id1", "id1.identifier.patientIdentifierSystem:id1");
    var mapName = "wSUYQUR3Y";
    var request =
        new TransportMappingRequest(
            "id1",
            "patientIdentifierSystem",
            ids,
            DEFAULT_DOMAINS,
            Duration.ofDays(14),
            DateShiftPreserve.NONE);
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
  void fetchSecureMapping() {
    given(redis.getMapCache(anyString())).willReturn(mapCache);
    given(mapCache.readAllMap())
        .willReturn(
            Mono.just(Map.of("id1", "123456789", "id2", "987654321", "dateShiftMillis", "12345")));
    create(mappingProvider.fetchSecureMapping("transferId"))
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
  void fetchSecureMappingWhenRedisDown() {
    given(redis.getMapCache(anyString())).willThrow(new RedisTimeoutException("timeout"));
    create(mappingProvider.fetchSecureMapping("transferId"))
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
  void fetchSecureMappingWithUnknownDomainException() {
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
  void fetchSecureMappingWithUnknownError() {
    wireMock.register(
        post("/$pseudonymizeAllowCreate")
            .willReturn(
                fhirResponse(
                    """
                    {
                      "resourceType": "OperationOutcome",
                      "issue": [{
                        "severity": "error",
                        "code": "processing",
                        "diagnostics": "Unknown error"
                      }]
                    }
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
  void fetchSecureMappingWrongDateShiftValue() {
    given(redis.getMapCache(anyString())).willReturn(mapCache);
    given(mapCache.readAllMap())
        .willReturn(
            Mono.just(Map.of("id1", "123456789", "id2", "987654321", "dateShiftMillis", "nan")));
    create(mappingProvider.fetchSecureMapping("transferId"))
        .expectErrorMessage("Invalid dateShiftMillis value: 'nan'")
        .verify();
  }

  @Nested
  class SaveSecureMappingTests {

    private static final String PATIENT_ID = "patient-id";
    private static final String PATIENT_ID_PSEUDONYM = "pseudo-patient-id";
    private static final String SALT = "testSalt";
    private static final String DATE_SHIFT_SEED = "dateShiftSeed";
    private static final Duration MAX_DATE_SHIFT = Duration.ofDays(365);

    private Map<String, String> transportMapping;

    @BeforeEach
    void setUp() {
      transportMapping =
          Map.of(
              "patient-id.identifier.patientIdentifierSystem:patient-id",
              "tpid",
              "id1",
              "tid1",
              "id2",
              "tid2");
    }

    @Test
    void saveSecureMappingCreatesCorrectMapping() {
      var transportMapping =
          Map.of(
              "id1",
              "tid1",
              "id2",
              "tid2",
              "patient-id.identifier.patientIdentifierSystem:patient-id",
              "tid3");
      @SuppressWarnings("unchecked")
      var mockRMap = (RMapReactive<String, String>) mock(RMapReactive.class);

      ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);
      given(mockRMap.putAll(mapCaptor.capture())).willReturn(Mono.empty());

      var saveFunction =
          FhirMappingProvider.saveSecureMapping(
              new TransportMappingRequest(
                  "patient-id",
                  "patientIdentifierSystem",
                  Set.of(),
                  new TcaDomains("", "", ""),
                  MAX_DATE_SHIFT,
                  DateShiftPreserve.NONE),
              transportMapping,
              mockRMap);

      var tuple =
          new FhirMappingProvider.PseudonymData(PATIENT_ID_PSEUDONYM, SALT, DATE_SHIFT_SEED);

      @SuppressWarnings("unchecked")
      Mono<Duration> result = saveFunction.apply(tuple);

      create(result).expectNextMatches(Objects::nonNull).verifyComplete();

      Map<String, String> savedMap = mapCaptor.getValue();
      assertThat(savedMap).containsKey("dateShiftMillis");
      assertThat(savedMap.get("dateShiftMillis")).matches("^-?\\d+$");

      assertThat(savedMap).containsKey("tid1");
      assertThat(savedMap).containsKey("tid2");
      assertThat(savedMap).containsKey("tid3");

      assertThat(savedMap.values()).contains(PATIENT_ID_PSEUDONYM);
    }

    @Test
    void shouldSaveSecureMappingWithAllRequiredData() {
      @SuppressWarnings("unchecked")
      var mockRMap = (RMapReactive<String, String>) mock(RMapReactive.class);
      given(mockRMap.putAll(anyMap())).willReturn(Mono.empty());
      var tuple =
          new FhirMappingProvider.PseudonymData(PATIENT_ID_PSEUDONYM, SALT, DATE_SHIFT_SEED);

      var function =
          FhirMappingProvider.saveSecureMapping(
              new TransportMappingRequest(
                  "patient-id",
                  "patientIdentifierSystem",
                  Set.of(),
                  new TcaDomains("", "", ""),
                  MAX_DATE_SHIFT,
                  DateShiftPreserve.NONE),
              transportMapping,
              mockRMap);

      create(function.apply(tuple))
          .assertNext(
              duration -> {
                assertThat(duration).isNotNull();
                assertThat(duration.toMillis()).isGreaterThanOrEqualTo(-MAX_DATE_SHIFT.toMillis());
                assertThat(duration.toMillis()).isLessThanOrEqualTo(MAX_DATE_SHIFT.toMillis());
              })
          .verifyComplete();
    }

    @Test
    void shouldIncludeSecureTransportMappings() {
      @SuppressWarnings("unchecked")
      var mockRMap = (RMapReactive<String, String>) mock(RMapReactive.class);
      given(mockRMap.putAll(anyMap())).willReturn(Mono.empty());
      var tuple =
          new FhirMappingProvider.PseudonymData(PATIENT_ID_PSEUDONYM, SALT, DATE_SHIFT_SEED);
      ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);

      var function =
          FhirMappingProvider.saveSecureMapping(
              new TransportMappingRequest(
                  "patient-id",
                  "patientIdentifierSystem",
                  Set.of(),
                  new TcaDomains("", "", ""),
                  MAX_DATE_SHIFT,
                  DateShiftPreserve.NONE),
              transportMapping,
              mockRMap);

      create(function.apply(tuple)).expectNextMatches(Objects::nonNull).verifyComplete();

      ArgumentCaptor<Map<String, String>> actualMapCaptor = ArgumentCaptor.forClass(Map.class);
      org.mockito.Mockito.verify(mockRMap).putAll(actualMapCaptor.capture());
      Map<String, String> savedMap = actualMapCaptor.getValue();

      // Should contain secure mappings for all transport values
      assertThat(savedMap).containsKey("tpid");
      assertThat(savedMap).containsKey("tid1");
      assertThat(savedMap).containsKey("tid2");

      // Values should be hashed (different from original transport keys)
      assertThat(savedMap.get("tpid")).isNotEqualTo(PATIENT_ID);
      assertThat(savedMap.get("tid1")).isNotEqualTo("id1");
      assertThat(savedMap.get("tid2")).isNotEqualTo("id2");
    }

    @Test
    void saveSecureMappingHandlesDifferentDateShiftPreserveOptions() {
      var transportMapping = Map.of("id1", "tid1");
      @SuppressWarnings("unchecked")
      var mockRMap = (RMapReactive<String, String>) mock(RMapReactive.class);
      given(mockRMap.putAll(anyMap())).willReturn(Mono.empty());

      for (DateShiftPreserve preserve : DateShiftPreserve.values()) {
        var saveFunction =
            FhirMappingProvider.saveSecureMapping(
                new TransportMappingRequest(
                    "patient-id",
                    "patientIdentifierSystem",
                    Set.of(),
                    new TcaDomains("", "", ""),
                    MAX_DATE_SHIFT,
                    preserve),
                transportMapping,
                mockRMap);

        var tuple =
            new FhirMappingProvider.PseudonymData(PATIENT_ID_PSEUDONYM, SALT, DATE_SHIFT_SEED);

        @SuppressWarnings("unchecked")
        Mono<Duration> result = saveFunction.apply(tuple);

        create(result).expectNextMatches(Objects::nonNull).verifyComplete();
      }
    }

    @Test
    void saveSecureMappingHandlesEmptyTransportMapping() {
      var transportMapping = Map.<String, String>of();
      @SuppressWarnings("unchecked")
      var mockRMap = (RMapReactive<String, String>) mock(RMapReactive.class);

      ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);
      given(mockRMap.putAll(mapCaptor.capture())).willReturn(Mono.empty());

      var saveFunction =
          FhirMappingProvider.saveSecureMapping(
              new TransportMappingRequest(
                  "patient-id",
                  "patientIdentifierSystem",
                  Set.of(),
                  new TcaDomains("", "", ""),
                  MAX_DATE_SHIFT,
                  DateShiftPreserve.NONE),
              transportMapping,
              mockRMap);

      var tuple =
          new FhirMappingProvider.PseudonymData(PATIENT_ID_PSEUDONYM, SALT, DATE_SHIFT_SEED);

      @SuppressWarnings("unchecked")
      Mono<Duration> result = saveFunction.apply(tuple);

      create(result).expectNextMatches(Objects::nonNull).verifyComplete();

      Map<String, String> savedMap = mapCaptor.getValue();
      assertThat(savedMap).containsKey("dateShiftMillis");
      assertThat(savedMap).hasSize(1); // Only dateShiftMillis should be present
    }

    @Test
    void saveSecureMappingHandlesRedisFailure() {
      var transportMapping = Map.of("id1", "tid1");
      @SuppressWarnings("unchecked")
      var mockRMap = (RMapReactive<String, String>) mock(RMapReactive.class);
      given(mockRMap.putAll(anyMap())).willReturn(Mono.error(new RuntimeException("Redis error")));

      var saveFunction =
          FhirMappingProvider.saveSecureMapping(
              new TransportMappingRequest(
                  "patient-id",
                  "patientIdentifierSystem",
                  Set.of(),
                  new TcaDomains("", "", ""),
                  MAX_DATE_SHIFT,
                  DateShiftPreserve.NONE),
              transportMapping,
              mockRMap);

      var tuple =
          new FhirMappingProvider.PseudonymData(PATIENT_ID_PSEUDONYM, SALT, DATE_SHIFT_SEED);

      @SuppressWarnings("unchecked")
      Mono<Duration> result = saveFunction.apply(tuple);

      create(result).expectError(RuntimeException.class).verify();
    }

    @Test
    void shouldProduceConsistentHashForSameSaltAndTransportId() {
      @SuppressWarnings("unchecked")
      var mockRMap = (RMapReactive<String, String>) mock(RMapReactive.class);
      given(mockRMap.putAll(anyMap())).willReturn(Mono.empty());
      var tuple =
          new FhirMappingProvider.PseudonymData(PATIENT_ID_PSEUDONYM, SALT, DATE_SHIFT_SEED);
      ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);

      var function =
          FhirMappingProvider.saveSecureMapping(
              new TransportMappingRequest(
                  "patient-id",
                  "patientIdentifierSystem",
                  Set.of(),
                  new TcaDomains("", "", ""),
                  MAX_DATE_SHIFT,
                  DateShiftPreserve.NONE),
              transportMapping,
              mockRMap);

      // First call
      create(function.apply(tuple)).expectNextMatches(Objects::nonNull).verifyComplete();

      // Capture first invocation
      org.mockito.Mockito.verify(mockRMap).putAll(mapCaptor.capture());
      Map<String, String> firstCall = mapCaptor.getValue();

      // Reset mock to prepare for the second invocation
      given(mockRMap.putAll(anyMap())).willReturn(Mono.empty());

      // Second call
      create(function.apply(tuple)).expectNextMatches(Objects::nonNull).verifyComplete();

      // Capture second invocation
      org.mockito.Mockito.verify(mockRMap, org.mockito.Mockito.times(2))
          .putAll(mapCaptor.capture());
      Map<String, String> secondCall = mapCaptor.getValue();

      // Hashed values should be identical
      assertThat(firstCall.get("tpid")).isEqualTo(secondCall.get("tpid"));
      assertThat(firstCall.get("tid1")).isEqualTo(secondCall.get("tid1"));
      assertThat(firstCall.get("tid2")).isEqualTo(secondCall.get("tid2"));
    }

    @Test
    void shouldProduceDifferentHashesForDifferentSalts() {
      @SuppressWarnings("unchecked")
      var mockRMap = (RMapReactive<String, String>) mock(RMapReactive.class);
      given(mockRMap.putAll(anyMap())).willReturn(Mono.empty());
      var tuple1 =
          new FhirMappingProvider.PseudonymData(PATIENT_ID_PSEUDONYM, "salt1", DATE_SHIFT_SEED);
      var tuple2 =
          new FhirMappingProvider.PseudonymData(PATIENT_ID_PSEUDONYM, "salt2", DATE_SHIFT_SEED);
      ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);

      var function1 =
          FhirMappingProvider.saveSecureMapping(
              new TransportMappingRequest(
                  "patient-id",
                  "patientIdentifierSystem",
                  Set.of(),
                  new TcaDomains("", "", ""),
                  MAX_DATE_SHIFT,
                  DateShiftPreserve.NONE),
              transportMapping,
              mockRMap);

      // First call with salt1
      create(function1.apply(tuple1)).expectNextMatches(Objects::nonNull).verifyComplete();

      // Capture first invocation
      org.mockito.Mockito.verify(mockRMap).putAll(mapCaptor.capture());
      Map<String, String> firstCall = mapCaptor.getValue();

      // Reset mock to prepare for the second invocation
      given(mockRMap.putAll(anyMap())).willReturn(Mono.empty());

      var function2 =
          FhirMappingProvider.saveSecureMapping(
              new TransportMappingRequest(
                  "patient-id",
                  "patientIdentifierSystem",
                  Set.of(),
                  new TcaDomains("", "", ""),
                  MAX_DATE_SHIFT,
                  DateShiftPreserve.NONE),
              transportMapping,
              mockRMap);

      // Second call with salt2
      create(function2.apply(tuple2)).expectNextMatches(Objects::nonNull).verifyComplete();

      // Capture second invocation
      org.mockito.Mockito.verify(mockRMap, org.mockito.Mockito.times(2))
          .putAll(mapCaptor.capture());
      Map<String, String> secondCall = mapCaptor.getValue();

      // Hashed values should be different
      assertThat(firstCall.get("tpid")).isEqualTo(secondCall.get("tpid")); // sPID stays the same
      assertThat(firstCall.get("tid1")).isNotEqualTo(secondCall.get("tid1"));
      assertThat(firstCall.get("tid2")).isNotEqualTo(secondCall.get("tid2"));
    }

    @Test
    void shouldHandleSpecialCharactersInTransportIds() {
      @SuppressWarnings("unchecked")
      var mockRMap = (RMapReactive<String, String>) mock(RMapReactive.class);
      given(mockRMap.putAll(anyMap())).willReturn(Mono.empty());
      var tuple =
          new FhirMappingProvider.PseudonymData(PATIENT_ID_PSEUDONYM, SALT, DATE_SHIFT_SEED);

      Map<String, String> specialCharMapping =
          Map.of(
              "transport@#$%_patient123", "value1",
              "transport-with-dashes_patient123", "value2",
              "transport.with.dots_patient123", "value3");

      var function =
          FhirMappingProvider.saveSecureMapping(
              new TransportMappingRequest(
                  "patient-id",
                  "patientIdentifierSystem",
                  Set.of(),
                  new TcaDomains("", "", ""),
                  MAX_DATE_SHIFT,
                  DateShiftPreserve.NONE),
              specialCharMapping,
              mockRMap);

      create(function.apply(tuple)).expectNextMatches(Objects::nonNull).verifyComplete();

      org.mockito.Mockito.verify(mockRMap).putAll(anyMap());
    }

    @Test
    void shouldHandleNullTransportMappingGracefully() {
      @SuppressWarnings("unchecked")
      var mockRMap = (RMapReactive<String, String>) mock(RMapReactive.class);
      var tuple =
          new FhirMappingProvider.PseudonymData(PATIENT_ID_PSEUDONYM, SALT, DATE_SHIFT_SEED);

      org.junit.jupiter.api.Assertions.assertThrows(
          NullPointerException.class,
          () -> {
            var function =
                FhirMappingProvider.saveSecureMapping(
                    new TransportMappingRequest(
                        "patient-id",
                        "patientIdentifierSystem",
                        Set.of(),
                        new TcaDomains("", "", ""),
                        MAX_DATE_SHIFT,
                        DateShiftPreserve.NONE),
                    null,
                    mockRMap);
            function.apply(tuple).block();
          });
    }

    @Test
    void shouldHandleVeryLargeTransportMappings() {
      @SuppressWarnings("unchecked")
      var mockRMap = (RMapReactive<String, String>) mock(RMapReactive.class);
      given(mockRMap.putAll(anyMap())).willReturn(Mono.empty());
      var tuple =
          new FhirMappingProvider.PseudonymData(PATIENT_ID_PSEUDONYM, SALT, DATE_SHIFT_SEED);

      // Create large mapping
      var largeMapping = new java.util.HashMap<String, String>();
      for (int i = 0; i < 10000; i++) {
        largeMapping.put("transport" + i + "_patient", "value" + i);
      }

      var function =
          FhirMappingProvider.saveSecureMapping(
              new TransportMappingRequest(
                  "patient-id",
                  "patientIdentifierSystem",
                  Set.of(),
                  new TcaDomains("", "", ""),
                  MAX_DATE_SHIFT,
                  DateShiftPreserve.NONE),
              largeMapping,
              mockRMap);

      create(function.apply(tuple)).expectNextMatches(Objects::nonNull).verifyComplete();
    }

    @Test
    void shouldHandleEmptyStringsInParameters() {
      @SuppressWarnings("unchecked")
      var mockRMap = (RMapReactive<String, String>) mock(RMapReactive.class);
      given(mockRMap.putAll(anyMap())).willReturn(Mono.empty());
      var tuple = new FhirMappingProvider.PseudonymData("", "", "");

      var function =
          FhirMappingProvider.saveSecureMapping(
              new TransportMappingRequest(
                  "patient-id",
                  "patientIdentifierSystem",
                  Set.of(),
                  new TcaDomains("", "", ""),
                  MAX_DATE_SHIFT,
                  DateShiftPreserve.NONE),
              transportMapping,
              mockRMap);

      create(function.apply(tuple)).expectNextMatches(Objects::nonNull).verifyComplete();
    }

    @Test
    void shouldProduceSha256LengthHashes() {
      @SuppressWarnings("unchecked")
      var mockRMap = (RMapReactive<String, String>) mock(RMapReactive.class);
      given(mockRMap.putAll(anyMap())).willReturn(Mono.empty());
      var tuple =
          new FhirMappingProvider.PseudonymData(PATIENT_ID_PSEUDONYM, SALT, DATE_SHIFT_SEED);
      ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);

      var function =
          FhirMappingProvider.saveSecureMapping(
              new TransportMappingRequest(
                  "patient-id",
                  "patientIdentifierSystem",
                  Set.of(),
                  new TcaDomains("", "", ""),
                  MAX_DATE_SHIFT,
                  DateShiftPreserve.NONE),
              transportMapping,
              mockRMap);

      create(function.apply(tuple)).expectNextMatches(Objects::nonNull).verifyComplete();

      org.mockito.Mockito.verify(mockRMap).putAll(mapCaptor.capture());
      Map<String, String> savedMap = mapCaptor.getValue();

      // SHA-256 hashes should be 64 characters long (hex representation)
      savedMap.entrySet().stream()
          .filter(entry -> !entry.getKey().equals("dateShiftMillis"))
          .filter(
              entry ->
                  !Objects.equals(
                      entry.getValue(), PATIENT_ID_PSEUDONYM)) // Skip patient ID mappings
          .forEach(
              entry -> {
                assertThat(entry.getValue()).hasSize(64).matches("^[a-f0-9]{64}$");
              });
    }

    @Test
    void shouldNotLeakOriginalTransportIdsInHashes() {
      @SuppressWarnings("unchecked")
      var mockRMap = (RMapReactive<String, String>) mock(RMapReactive.class);
      given(mockRMap.putAll(anyMap())).willReturn(Mono.empty());
      var tuple =
          new FhirMappingProvider.PseudonymData(PATIENT_ID_PSEUDONYM, SALT, DATE_SHIFT_SEED);
      ArgumentCaptor<Map<String, String>> mapCaptor = ArgumentCaptor.forClass(Map.class);

      var function =
          FhirMappingProvider.saveSecureMapping(
              new TransportMappingRequest(
                  "patient-id",
                  "patientIdentifierSystem",
                  Set.of(),
                  new TcaDomains("", "", ""),
                  MAX_DATE_SHIFT,
                  DateShiftPreserve.NONE),
              transportMapping,
              mockRMap);

      create(function.apply(tuple)).expectNextMatches(Objects::nonNull).verifyComplete();

      org.mockito.Mockito.verify(mockRMap).putAll(mapCaptor.capture());
      Map<String, String> savedMap = mapCaptor.getValue();

      // Hashed values should not contain any part of original transport IDs
      transportMapping
          .keySet()
          .forEach(
              originalId -> {
                savedMap
                    .values()
                    .forEach(
                        hashedValue -> {
                          if (!hashedValue.equals(PATIENT_ID_PSEUDONYM)
                              && !hashedValue.matches("^-?\\d+$")) {
                            assertThat(hashedValue).doesNotContain(originalId);
                          }
                        });
              });
    }
  }

  @Nested
  class GenerateSecureMappingTests {

    @Test
    void generateSecureMappingCreatesHashedMappings() {
      var transportSalt = "testSalt";
      var transportMapping = Map.of("id1", "tid1", "id2", "tid2", "id3", "tid3");

      var result = FhirMappingProvider.generateSecureMapping(transportSalt, transportMapping);

      assertThat(result).hasSize(3);
      assertThat(result).containsKey("tid1");
      assertThat(result).containsKey("tid2");
      assertThat(result).containsKey("tid3");

      // All values should be SHA-256 hashes (64 hex characters)
      result
          .values()
          .forEach(
              hash -> {
                assertThat(hash).hasSize(64);
                assertThat(hash).matches("^[a-f0-9]{64}$");
              });
    }

    @Test
    void generateSecureMappingProducesConsistentHashes() {
      var transportSalt = "testSalt";
      var transportMapping = Map.of("id1", "tid1", "id2", "tid2");

      var result1 = FhirMappingProvider.generateSecureMapping(transportSalt, transportMapping);
      var result2 = FhirMappingProvider.generateSecureMapping(transportSalt, transportMapping);

      assertThat(result1).isEqualTo(result2);
    }

    @Test
    void generateSecureMappingHandlesDifferentSalts() {
      var transportMapping = Map.of("id1", "tid1");

      var result1 = FhirMappingProvider.generateSecureMapping("salt1", transportMapping);
      var result2 = FhirMappingProvider.generateSecureMapping("salt2", transportMapping);

      assertThat(result1.get("tid1")).isNotEqualTo(result2.get("tid1"));
    }

    @Test
    void generateSecureMappingHandlesEmptyMapping() {
      var transportSalt = "testSalt";
      var transportMapping = Map.<String, String>of();

      var result = FhirMappingProvider.generateSecureMapping(transportSalt, transportMapping);

      assertThat(result).isEmpty();
    }

    @Test
    void generateSecureMappingHandlesSpecialCharacters() {
      var transportSalt = "testSalt";
      var transportMapping =
          Map.of(
              "id@#$%", "tid1",
              "id-with-dashes", "tid2",
              "id.with.dots", "tid3",
              "id_with_underscores", "tid4");

      var result = FhirMappingProvider.generateSecureMapping(transportSalt, transportMapping);

      assertThat(result).hasSize(4);
      result
          .values()
          .forEach(
              hash -> {
                assertThat(hash).hasSize(64);
                assertThat(hash).matches("^[a-f0-9]{64}$");
              });
    }

    @Test
    void generateSecureMappingHandlesLargeMapping() {
      var transportSalt = "testSalt";
      var transportMapping =
          IntStream.range(0, 1000).boxed().collect(Collectors.toMap(i -> "id" + i, i -> "tid" + i));

      var result = FhirMappingProvider.generateSecureMapping(transportSalt, transportMapping);

      assertThat(result).hasSize(1000);
      result
          .values()
          .forEach(
              hash -> {
                assertThat(hash).hasSize(64);
                assertThat(hash).matches("^[a-f0-9]{64}$");
              });
    }
  }

  @Nested
  class PatientIdPseudonymsTests {

    @Test
    void patientIdPseudonymsFiltersCorrectKeys() {
      var patientId = "patient123";
      var patientIdPseudonym = "pseudonym456";
      var transportMapping =
          Map.of(
              "Observation.obs1", "tid1",
              "Patient.patient123", "tid2",
              "Encounter.enc1", "tid3",
              "patient123.identifier.patientIdentifierSystem:patient123", "tid4",
              "Patient.otherpatient", "tid5",
              "some.other.patient123", "tid6");

      var result =
          FhirMappingProvider.patientIdPseudonyms(
              patientId, "patientIdentifierSystem", patientIdPseudonym, transportMapping);

      assertThat(result).hasSize(1);
      assertThat(result).containsEntry("tid4", patientIdPseudonym);
      assertThat(result).doesNotContainKey("tid1");
      assertThat(result).doesNotContainKey("tid2");
      assertThat(result).doesNotContainKey("tid3");
      assertThat(result).doesNotContainKey("tid5");
      assertThat(result).doesNotContainKey("tid6");
    }

    @Test
    void patientIdPseudonymsHandlesEmptyMapping() {
      var patientId = "patient123";
      var patientIdPseudonym = "pseudonym456";
      var transportMapping = Map.<String, String>of();

      var result =
          FhirMappingProvider.patientIdPseudonyms(
              "patient-id", "patientIdentifierSystem", patientIdPseudonym, transportMapping);

      assertThat(result).isEmpty();
    }

    @Test
    void patientIdPseudonymsHandlesNoMatches() {
      var patientId = "patient123";
      var patientIdPseudonym = "pseudonym456";
      var transportMapping =
          Map.of(
              "Observation.obs1", "tid1",
              "Encounter.enc1", "tid2",
              "Patient.differentpatient", "tid3");

      var result =
          FhirMappingProvider.patientIdPseudonyms(
              patientId, "patientIdentifierSystem", patientIdPseudonym, transportMapping);

      assertThat(result).isEmpty();
    }

    @Test
    void patientIdPseudonymsHandlesExactMatch() {
      var patientId = "patient123";
      var patientIdPseudonym = "pseudonym456";
      var transportMapping =
          Map.of("patient123.identifier.patientIdentifierSystem:patient123", "tid1");

      var result =
          FhirMappingProvider.patientIdPseudonyms(
              patientId, "patientIdentifierSystem", patientIdPseudonym, transportMapping);

      assertThat(result).hasSize(1);
      assertThat(result).containsEntry("tid1", patientIdPseudonym);
    }

    @Test
    void patientIdPseudonymsHandlesPartialMatches() {
      var patientId = "123";
      var patientIdPseudonym = "pseudonym456";
      var transportMapping =
          Map.of(
              "123.identifier.patientIdentifierSystem:123", "tid1",
              "other.patientIdentifierSystem:123", "tid2",
              "patientIdentifierSystem:456", "tid3",
              "differentKey", "tid4");

      var result =
          FhirMappingProvider.patientIdPseudonyms(
              patientId, "patientIdentifierSystem", patientIdPseudonym, transportMapping);

      assertThat(result).hasSize(1);
      assertThat(result).containsEntry("tid1", patientIdPseudonym);
      assertThat(result).doesNotContainKey("tid2");
      assertThat(result).doesNotContainKey("tid3");
      assertThat(result).doesNotContainKey("tid4");
    }

    @Test
    void patientIdPseudonymsHandlesSpecialCharacters() {
      var patientId = "patient@#$%";
      var patientIdPseudonym = "pseudonym456";
      var transportMapping =
          Map.of("patient@#$%.identifier.patientIdentifierSystem:patient@#$%", "tid1");

      var result =
          FhirMappingProvider.patientIdPseudonyms(
              patientId, "patientIdentifierSystem", patientIdPseudonym, transportMapping);

      assertThat(result).containsEntry("tid1", patientIdPseudonym);
    }
  }

  @AfterEach
  void tearDown() {
    wireMock.resetMappings();
  }
}
