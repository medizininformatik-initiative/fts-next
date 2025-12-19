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
  }

  @AfterEach
  void tearDown() {
    gpas().resetMappings();
    redisClient.getKeys().deleteByPattern("dateshift:*");
  }

  @Test
  void generateCdDateShift_shouldReturnTransferIdAndDateShift() throws IOException {
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
            .bodyToMono(DateShiftResponse.class);

    create(response)
        .assertNext(
            resp -> {
              assertThat(resp).isNotNull();
              assertThat(resp.transferId())
                  .isNotNull()
                  .hasSize(32)
                  .matches(s -> s.matches("^[A-Za-z0-9_-]+$"), "should be Base64URL encoded");
              assertThat(resp.dateShiftDays())
                  .isBetween((int) -MAX_DATE_SHIFT.toDays(), (int) MAX_DATE_SHIFT.toDays());
            })
        .verifyComplete();
  }

  @Test
  void generateCdDateShift_shouldStoreDateShiftInRedis() throws IOException {
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
            .bodyToMono(DateShiftResponse.class)
            .block();

    assertThat(resp).isNotNull();

    // Verify dateshift was stored in Redis
    var keys = redisClient.getKeys().getKeysByPattern("dateshift:*");
    assertThat(keys).isNotEmpty();

    // The stored value should be accessible via the transferId
    var storedDateShift = redisClient.<Integer>getBucket("dateshift:" + resp.transferId()).get();
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
            .bodyToMono(DateShiftResponse.class)
            .block();

    // Reset gPAS mock and make second request
    gpas().resetMappings();
    stubGpasForDateShift("patient-deterministic", "fixed-seed-xyz");

    var resp2 =
        cdClient
            .post()
            .uri(CD_DATESHIFT_ENDPOINT)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(DateShiftResponse.class)
            .block();

    assertThat(resp1).isNotNull();
    assertThat(resp2).isNotNull();
    // Same seed should produce same date shift
    assertThat(resp1.dateShiftDays()).isEqualTo(resp2.dateShiftDays());
    // But different transfer IDs (they are random)
    assertThat(resp1.transferId()).isNotEqualTo(resp2.transferId());
  }

  @Test
  void getRdDateShift_shouldReturnStoredDateShift() throws IOException {
    // First generate a date shift via CDA endpoint
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
            .bodyToMono(DateShiftResponse.class)
            .block();

    assertThat(cdResponse).isNotNull();

    // Now retrieve the RDA date shift
    var rdResponse =
        cdClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path(RD_DATESHIFT_ENDPOINT)
                        .queryParam("transferId", cdResponse.transferId())
                        .build())
            .retrieve()
            .bodyToMono(DateShiftResponse.class);

    create(rdResponse)
        .assertNext(
            resp -> {
              assertThat(resp).isNotNull();
              assertThat(resp.transferId()).isEqualTo(cdResponse.transferId());
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
  void getRdDateShift_withUnknownTransferId_shouldReturn404() {
    var response =
        cdClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path(RD_DATESHIFT_ENDPOINT)
                        .queryParam("transferId", "nonexistent-transfer-id")
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
            .bodyToMono(DateShiftResponse.class);

    create(response)
        .assertNext(
            resp -> {
              assertThat(resp).isNotNull();
              assertThat(resp.transferId()).isNotNull().hasSize(32);
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
            .bodyToMono(DateShiftResponse.class);

    create(response)
        .assertNext(
            resp -> {
              assertThat(resp).isNotNull();
              assertThat(resp.transferId()).isNotNull().hasSize(32);
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
}
