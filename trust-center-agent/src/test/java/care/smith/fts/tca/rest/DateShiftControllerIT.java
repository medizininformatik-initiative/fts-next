package care.smith.fts.tca.rest;

import static care.smith.fts.test.FhirGenerators.fromList;
import static care.smith.fts.test.FhirGenerators.gpasGetOrCreateResponse;
import static care.smith.fts.test.MockServerUtil.APPLICATION_FHIR_JSON;
import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.DateShiftPreserve;
import care.smith.fts.tca.BaseIT;
import care.smith.fts.test.TestWebClientFactory;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Integration tests for DateShiftController.
 *
 * <p>Tests the date shift endpoints used by FHIR Pseudonymizer for deterministic date shifting.
 */
@Slf4j
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestWebClientFactory.class)
class DateShiftControllerIT extends BaseIT {

  private static final String CD_DATESHIFT_ENDPOINT = "/api/v2/cd/dateshift";
  private static final String RD_DATESHIFT_ENDPOINT = "/api/v2/rd/dateshift";
  private static final Duration MAX_DATE_SHIFT = Duration.ofDays(14);
  private static final String DATESHIFT_DOMAIN = "dateshift-domain";

  @Autowired private RedissonClient redisClient;
  private WebClient cdClient;

  @BeforeEach
  void setUp(@LocalServerPort int port, @Autowired TestWebClientFactory factory) {
    cdClient = factory.webClient("https://localhost:" + port, "cd-agent");
    redisClient.getKeys().deleteByPattern("dateshift:*");
    redisClient.getKeys().deleteByPattern("temp-dateshift:*");
    redisClient.getKeys().deleteByPattern("tid:*");
  }

  @AfterEach
  void tearDown() {
    gpas().resetMappings();
    redisClient.getKeys().deleteByPattern("dateshift:*");
    redisClient.getKeys().deleteByPattern("temp-dateshift:*");
    redisClient.getKeys().deleteByPattern("tid:*");
  }

  @Test
  void generateCdDateShift_shouldReturnDateShift() throws IOException {
    stubGpasForDateShift("patient-123", "seed-abc");

    var request =
        new DateShiftRequest(
            "patient-123", MAX_DATE_SHIFT, DateShiftPreserve.NONE, DATESHIFT_DOMAIN);

    var response =
        cdClient
            .post()
            .uri(CD_DATESHIFT_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(CdDateShiftResponse.class);

    create(response)
        .assertNext(
            resp -> {
              assertThat(resp).isNotNull();
              assertThat(resp.dateShiftDays())
                  .isBetween((int) -MAX_DATE_SHIFT.toDays(), (int) MAX_DATE_SHIFT.toDays());
            })
        .verifyComplete();
  }

  @Test
  void generateCdDateShift_shouldStoreTempDateShiftInRedis() throws IOException {
    stubGpasForDateShift("patient-456", "seed-stored");

    var request =
        new DateShiftRequest(
            "patient-456", MAX_DATE_SHIFT, DateShiftPreserve.NONE, DATESHIFT_DOMAIN);

    var resp =
        cdClient
            .post()
            .uri(CD_DATESHIFT_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(CdDateShiftResponse.class)
            .block();

    assertThat(resp).isNotNull();

    // Verify temp dateshift was stored in Redis keyed by patientId
    var keys = redisClient.getKeys().getKeysByPattern("temp-dateshift:*");
    assertThat(keys).isNotEmpty();

    // The stored value should be accessible via the patientId
    var storedDateShift = redisClient.<Integer>getBucket("temp-dateshift:patient-456").get();
    assertThat(storedDateShift).isNotNull();
  }

  @Test
  void generateCdDateShift_shouldBeDeterministic() throws IOException {
    // Same seed should produce same results
    stubGpasForDateShift("patient-deterministic", "fixed-seed-xyz");

    var request =
        new DateShiftRequest(
            "patient-deterministic", MAX_DATE_SHIFT, DateShiftPreserve.NONE, DATESHIFT_DOMAIN);

    var resp1 =
        cdClient
            .post()
            .uri(CD_DATESHIFT_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(CdDateShiftResponse.class)
            .block();

    // Reset gPAS mock and temp storage, make second request
    gpas().resetMappings();
    redisClient.getKeys().deleteByPattern("temp-dateshift:*");
    stubGpasForDateShift("patient-deterministic", "fixed-seed-xyz");

    var resp2 =
        cdClient
            .post()
            .uri(CD_DATESHIFT_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(CdDateShiftResponse.class)
            .block();

    assertThat(resp1).isNotNull();
    assertThat(resp2).isNotNull();
    // Same seed should produce same date shift
    assertThat(resp1.dateShiftDays()).isEqualTo(resp2.dateShiftDays());
  }

  @Test
  void getRdDateShift_shouldReturnStoredDateShift() throws IOException {
    // Step 1: Generate a date shift via CDA dateshift endpoint (stores temp-dateshift:<patientId>)
    stubGpasForDateShift("patient-rd-test", "seed-rd");

    var cdRequest =
        new DateShiftRequest(
            "patient-rd-test", MAX_DATE_SHIFT, DateShiftPreserve.NONE, DATESHIFT_DOMAIN);

    var cdResponse =
        cdClient
            .post()
            .uri(CD_DATESHIFT_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(cdRequest)
            .retrieve()
            .bodyToMono(CdDateShiftResponse.class)
            .block();

    assertThat(cdResponse).isNotNull();

    // Step 2: Call create-pseudonym to link dateshift to transportId
    stubGpasForPseudonym("patient-rd-test", "sID-test-pseudonym");

    var vfpsRequest = buildVfpsRequest(DATESHIFT_DOMAIN, "patient-rd-test");
    var vfpsResponse =
        cdClient
            .post()
            .uri("/api/v2/cd/fhir/$create-pseudonym")
            .header(CONTENT_TYPE, APPLICATION_FHIR_JSON)
            .header("Accept", APPLICATION_FHIR_JSON)
            .bodyValue(vfpsRequest)
            .retrieve()
            .bodyToMono(Parameters.class)
            .block();

    assertThat(vfpsResponse).isNotNull();
    var transportId = extractPseudonymValue(vfpsResponse);
    assertThat(transportId).isNotNull().hasSize(32);

    // Step 3: Retrieve the RDA date shift using the transportId
    var rdResponse =
        cdClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path(RD_DATESHIFT_ENDPOINT)
                        .queryParam("transportId", transportId)
                        .build())
            .retrieve()
            .bodyToMono(DateShiftResponse.class);

    create(rdResponse)
        .assertNext(
            resp -> {
              assertThat(resp).isNotNull();
              assertThat(resp.transportId()).isEqualTo(transportId);
              // RDA date shift can be up to 2*maxDateShift because:
              // rdDateShift = totalShift - cdDateShift
              // If cdDateShift is -max and totalShift is +max, rdDateShift = +2*max
              assertThat(resp.dateShiftDays())
                  .isBetween(
                      (int) (-2 * MAX_DATE_SHIFT.toDays()), (int) (2 * MAX_DATE_SHIFT.toDays()));
            })
        .verifyComplete();
  }

  @Test
  void getRdDateShift_withUnknownTransportId_shouldReturn404() {
    var response =
        cdClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path(RD_DATESHIFT_ENDPOINT)
                        .queryParam("transportId", "nonexistent-transport-id")
                        .build())
            .retrieve()
            .toBodilessEntity();

    create(response)
        .expectErrorSatisfies(
            e -> {
              assertThat(e).isInstanceOf(WebClientResponseException.class);
              assertThat(((WebClientResponseException) e).getStatusCode())
                  .isEqualTo(HttpStatus.NOT_FOUND);
            })
        .verify();
  }

  @Test
  void generateCdDateShift_withWeekdayPreserve_shouldReturnValidShift() throws IOException {
    stubGpasForDateShift("patient-weekday", "seed-weekday");

    var request =
        new DateShiftRequest(
            "patient-weekday", MAX_DATE_SHIFT, DateShiftPreserve.WEEKDAY, DATESHIFT_DOMAIN);

    var response =
        cdClient
            .post()
            .uri(CD_DATESHIFT_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(CdDateShiftResponse.class);

    create(response)
        .assertNext(
            resp -> {
              assertThat(resp).isNotNull();
              assertThat(resp.dateShiftDays())
                  .isBetween((int) -MAX_DATE_SHIFT.toDays(), (int) MAX_DATE_SHIFT.toDays());
            })
        .verifyComplete();
  }

  @Test
  void generateCdDateShift_withDaytimePreserve_shouldReturnValidShift() throws IOException {
    stubGpasForDateShift("patient-daytime", "seed-daytime");

    var request =
        new DateShiftRequest(
            "patient-daytime", MAX_DATE_SHIFT, DateShiftPreserve.DAYTIME, DATESHIFT_DOMAIN);

    var response =
        cdClient
            .post()
            .uri(CD_DATESHIFT_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(CdDateShiftResponse.class);

    create(response)
        .assertNext(
            resp -> {
              assertThat(resp).isNotNull();
              assertThat(resp.dateShiftDays())
                  .isBetween((int) -MAX_DATE_SHIFT.toDays(), (int) MAX_DATE_SHIFT.toDays());
            })
        .verifyComplete();
  }

  @Test
  void generateCdDateShift_withMissingPatientId_shouldReturn400() {
    var invalidRequest =
        """
        {
          "maxDateShift": "PT336H",
          "dateShiftPreserve": "NONE",
          "dateShiftDomain": "dateshift-domain"
        }
        """;

    var response =
        cdClient
            .post()
            .uri(CD_DATESHIFT_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidRequest)
            .retrieve()
            .toBodilessEntity();

    create(response)
        .expectErrorSatisfies(
            e -> {
              assertThat(e).isInstanceOf(WebClientResponseException.class);
              assertThat(((WebClientResponseException) e).getStatusCode())
                  .isEqualTo(HttpStatus.BAD_REQUEST);
            })
        .verify();
  }

  @Test
  void generateCdDateShift_withMissingMaxDateShift_shouldReturn400() {
    var invalidRequest =
        """
        {
          "patientId": "patient-123",
          "dateShiftPreserve": "NONE",
          "dateShiftDomain": "dateshift-domain"
        }
        """;

    var response =
        cdClient
            .post()
            .uri(CD_DATESHIFT_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(invalidRequest)
            .retrieve()
            .toBodilessEntity();

    create(response)
        .expectErrorSatisfies(
            e -> {
              assertThat(e).isInstanceOf(WebClientResponseException.class);
              assertThat(((WebClientResponseException) e).getStatusCode())
                  .isEqualTo(HttpStatus.BAD_REQUEST);
            })
        .verify();
  }

  private void stubGpasForDateShift(String patientId, String seed) throws IOException {
    var dateShiftKey = "%s_%s".formatted(MAX_DATE_SHIFT.toString(), patientId);
    var fhirGenerator =
        gpasGetOrCreateResponse(fromList(List.of(dateShiftKey)), fromList(List.of(seed)));

    gpas()
        .register(
            post(urlEqualTo("/ttp-fhir/fhir/gpas/$pseudonymizeAllowCreate"))
                .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON))
                .willReturn(fhirResponse(fhirGenerator.generateString())));
  }

  private void stubGpasForPseudonym(String patientId, String pseudonym) throws IOException {
    var fhirGenerator =
        gpasGetOrCreateResponse(fromList(List.of(patientId)), fromList(List.of(pseudonym)));

    gpas()
        .register(
            post(urlEqualTo("/ttp-fhir/fhir/gpas/$pseudonymizeAllowCreate"))
                .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON))
                .willReturn(fhirResponse(fhirGenerator.generateString())));
  }

  private Parameters buildVfpsRequest(String namespace, String originalValue) {
    var params = new Parameters();
    params.addParameter().setName("namespace").setValue(new StringType(namespace));
    params.addParameter().setName("originalValue").setValue(new StringType(originalValue));
    return params;
  }

  private String extractPseudonymValue(Parameters params) {
    return params.getParameter().stream()
        .filter(p -> "pseudonymValue".equals(p.getName()))
        .findFirst()
        .map(p -> p.getValue().primitiveValue())
        .orElse(null);
  }
}
