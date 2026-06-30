package care.smith.fts.tca.rest;

import static care.smith.fts.test.FhirGenerators.gpasResponse;
import static care.smith.fts.test.MockServerUtil.APPLICATION_FHIR_JSON;
import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static java.time.Duration.ofDays;
import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.BodyInserters.fromValue;
import static reactor.test.StepVerifier.create;

import care.smith.fts.tca.BaseIT;
import care.smith.fts.test.TestWebClientFactory;
import care.smith.fts.util.tca.SecureMappingResponse;
import care.smith.fts.util.tca.TransportMappingResponse;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Slf4j
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestWebClientFactory.class)
class DeIdentificationControllerIT extends BaseIT {

  private static final Map<String, String> DEFAULT_DOMAINS =
      ofEntries(entry("pseudonym", "MII"), entry("salt", "MII"), entry("dateShift", "MII"));
  private static final UrlPattern GPAS_URL =
      urlEqualTo("/ttp-fhir/fhir/gpas/$pseudonymizeAllowCreate");
  private static final Map<String, String> GPAS_PSEUDONYMS =
      Map.of(
          "id-144218", "469680023",
          "Salt_id-144218", "123",
          "PT336H_id-144218", "12345");

  private WebClient cdClient;
  private WebClient rdClient;

  @BeforeEach
  void setUp(
      @LocalServerPort int cdPort,
      @LocalServerPort int rdPort,
      @Autowired TestWebClientFactory factory) {
    cdClient = factory.webClient("https://localhost:" + cdPort, "cd-agent");
    rdClient = factory.webClient("https://localhost:" + rdPort, "rd-agent");
  }

  @Test
  void successfulRequest() {
    registerGpas();

    var response =
        doPost(
            ofEntries(
                entry("tcaDomains", DEFAULT_DOMAINS),
                entry("patientIdentifier", "id-144218"),
                entry("patientIdentifierSystem", "http://fts.smith.care"),
                entry("idMappings", Map.of("id-144218", "tid1", "id-244194", "tid2")),
                entry("dateMappings", Map.of()),
                entry("maxDateShift", ofDays(14).getSeconds()),
                entry("dateShiftPreserve", "NONE")));

    create(response)
        .assertNext(
            res -> {
              assertThat(res).isNotNull();
              assertThat(res.transferId()).isNotNull();
            })
        .verifyComplete();

    // All three same-domain keys are resolved in a single gPAS call.
    gpas().verifyThat(1, postRequestedFor(GPAS_URL));
  }

  @Test
  void firstRequestToGpasFails() {
    gpas()
        .register(
            post(GPAS_URL)
                .inScenario("gpas-retry")
                .whenScenarioStateIs(STARTED)
                .willReturn(aResponse().withStatus(500))
                .willSetStateTo("recovered"));
    gpas()
        .register(
            post(GPAS_URL)
                .inScenario("gpas-retry")
                .whenScenarioStateIs("recovered")
                .willReturn(fhirResponse(gpasResponse(GPAS_PSEUDONYMS))));

    var response =
        doPost(
            ofEntries(
                entry("tcaDomains", DEFAULT_DOMAINS),
                entry("patientIdentifier", "id-144218"),
                entry("patientIdentifierSystem", "http://fts.smith.care"),
                entry("idMappings", Map.of("id-144218", "tid1", "id-244194", "tid2")),
                entry("dateMappings", Map.of()),
                entry("maxDateShift", ofDays(14).getSeconds()),
                entry("dateShiftPreserve", "NONE")));

    create(response)
        .assertNext(
            res -> {
              assertThat(res).isNotNull();
              assertThat(res.transferId()).isNotNull();
            })
        .verifyComplete();

    // The initial 500 is retried, so gPAS sees the batched request twice.
    gpas().verifyThat(2, postRequestedFor(GPAS_URL));
  }

  @Test
  void rejectInvalidIds() {
    var response =
        rdClient
            .post()
            .uri("/api/v2/rd/secure-mapping")
            .contentType(APPLICATION_JSON)
            .body(fromValue("username=Guest'%0AUser:'Admin"))
            .accept(APPLICATION_JSON)
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
  void transportMappingIdsAndDateShiftingValuesAndFetchPseudonyms() {
    registerGpas();

    var transferId =
        doPost(
                ofEntries(
                    entry("tcaDomains", DEFAULT_DOMAINS),
                    entry("patientIdentifier", "id-144218"),
                    entry("patientIdentifierSystem", "http://fts.smith.care"),
                    entry("idMappings", Map.of("id-144218", "tid1", "id-244194", "tid2")),
                    entry("dateMappings", Map.of("tId-date-1", "2024-03-15")),
                    entry("maxDateShift", ofDays(14).getSeconds()),
                    entry("dateShiftPreserve", "NONE")))
            .block()
            .transferId();

    var response =
        rdClient
            .post()
            .uri("/api/v2/rd/secure-mapping")
            .contentType(APPLICATION_JSON)
            .body(fromValue(transferId))
            .accept(APPLICATION_JSON)
            .retrieve()
            .toEntity(SecureMappingResponse.class);

    create(response)
        .assertNext(
            res -> {
              assertThat(res).isNotNull();
              assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
              var body = res.getBody();
              assertThat(body).isNotNull();
              assertThat(body.dateShiftMap()).containsKey("tId-date-1");
            })
        .verifyComplete();
  }

  private void registerGpas() {
    gpas()
        .register(
            post(GPAS_URL)
                .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON))
                .willReturn(fhirResponse(gpasResponse(GPAS_PSEUDONYMS))));
  }

  private Mono<TransportMappingResponse> doPost(Map<String, Object> body) {
    return cdClient
        .post()
        .uri("/api/v2/cd/transport-mapping")
        .contentType(APPLICATION_JSON)
        .accept(APPLICATION_JSON)
        .bodyValue(body)
        .retrieve()
        .bodyToMono(TransportMappingResponse.class);
  }

  @AfterEach
  void tearDown() {
    gics().resetMappings();
    gpas().resetMappings();
  }
}
