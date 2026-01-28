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

  private static final TcaDomains DEFAULT_DOMAINS = new TcaDomains("domain", "domain", "domain");
  private static final TransportMappingRequest DEFAULT_REQUEST =
      new TransportMappingRequest(
          "id1",
          "patientIdentifierSystem",
          Set.of("id1"),
          Map.of(),
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
            Map.of(),
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
              assertThat(r.dateShiftMapping()).isEmpty();
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
            Mono.just(
                Map.of(
                    "id1", "123456789",
                    "id2", "987654321",
                    "ds:2024-03-15", "2024-03-20")));
    create(mappingProvider.fetchSecureMapping("transferId"))
        .assertNext(
            m -> {
              assertThat(m.tidPidMap().keySet()).containsExactlyInAnyOrder("id1", "id2");
              assertThat(m.tidPidMap().values())
                  .containsExactlyInAnyOrder("123456789", "987654321");
              assertThat(m.dateShiftMap()).containsEntry("2024-03-15", "2024-03-20");
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
