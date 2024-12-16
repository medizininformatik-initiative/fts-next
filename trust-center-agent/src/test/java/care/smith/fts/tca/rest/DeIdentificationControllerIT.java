package care.smith.fts.tca.rest;

import static care.smith.fts.test.FhirGenerators.fromList;
import static care.smith.fts.test.FhirGenerators.gpasGetOrCreateResponse;
import static care.smith.fts.test.MockServerUtil.APPLICATION_FHIR_JSON;
import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
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
import care.smith.fts.util.tca.ResearchMappingResponse;
import care.smith.fts.util.tca.TransportMappingResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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

  private static WebClient cdClient;
  private static WebClient rdClient;

  @BeforeAll
  static void setUp(@LocalServerPort int port, @Autowired TestWebClientFactory factory) {
    cdClient = factory.webClient("https://localhost:" + port, "cd-agent");
    rdClient = factory.webClient("https://localhost:" + port, "rd-agent");
  }

  @Test
  void successfulRequest() throws IOException {
    var fhirGenerator =
        gpasGetOrCreateResponse(
            fromList(List.of("id-144218", "Salt_id-144218", "PT336H_id-144218")),
            fromList(List.of("469680023", "123", "12345")));

    List.of("id-144218", "Salt_id-144218", "PT336H_id-144218")
        .forEach(
            key ->
                gpas()
                    .register(
                        post(urlEqualTo("/ttp-fhir/fhir/gpas/$pseudonymizeAllowCreate"))
                            .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON))
                            .withRequestBody(
                                equalToJson(
                                    """
                                    { "resourceType": "Parameters",
                                      "parameter": [
                                        {"name": "target", "valueString": "MII"},
                                        {"name": "original", "valueString": "%s"}]}
                                    """
                                        .formatted(key),
                                    true,
                                    true))
                            .willReturn(fhirResponse(fhirGenerator.generateString(), 200))));

    var response =
        doPost(
            ofEntries(
                entry(
                    "tcaDomains",
                    ofEntries(
                        entry("pseudonym", "MII"),
                        entry("salt", "MII"),
                        entry("dateShift", "MII"))),
                entry("patientId", "id-144218"),
                entry("resourceIds", Set.of("id-144218", "id-244194")),
                entry("maxDateShift", ofDays(14).getSeconds())));

    create(response)
        .assertNext(
            res -> {
              assertThat(res).isNotNull();
              assertThat(res.dateShiftValue().toDays()).isBetween(-140L, 140L);
              assertThat(res.transportMapping()).containsKeys("id-144218", "id-244194");
            })
        .verifyComplete();
  }

  @Test
  void firstRequestToGpasFails() throws IOException {
    var statusCodes = new LinkedList<>(List.of(500));

    var map =
        Map.of("id-144218", "469680023", "Salt_id-144218", "123", "PT336H_id-144218", "12345");

    for (String s : List.of("id-144218", "Salt_id-144218", "PT336H_id-144218")) {
      String body = gpasGetOrCreateResponse(() -> s, () -> map.get(s)).generateString();
      gpas()
          .register(
              post(urlEqualTo("/ttp-fhir/fhir/gpas/$pseudonymizeAllowCreate"))
                  .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON))
                  .withRequestBody(
                      equalToJson(
                          """
                              { "resourceType": "Parameters",
                                "parameter": [
                                  {"name": "target", "valueString": "MII"},
                                  {"name": "original", "valueString": "%s"}]}
                              """
                              .formatted(s),
                          true,
                          true))
                  .willReturn(fhirResponse(body, 200)));
    }

    var response =
        doPost(
            ofEntries(
                entry(
                    "tcaDomains",
                    ofEntries(
                        entry("pseudonym", "MII"),
                        entry("salt", "MII"),
                        entry("dateShift", "MII"))),
                entry("patientId", "id-144218"),
                entry("resourceIds", Set.of("id-144218", "id-244194")),
                entry("maxDateShift", ofDays(14).getSeconds())));

    create(response)
        .assertNext(
            res -> {
              assertThat(res).isNotNull();
              assertThat(res.dateShiftValue().toDays()).isBetween(-140L, 140L);
              assertThat(res.transportMapping()).containsKeys("id-144218", "id-244194");
            })
        .verifyComplete();
  }

  @Test
  void rejectInvalidIds() {
    var response =
        rdClient
            .post()
            .uri("/api/v2/rd/research-mapping")
            .contentType(APPLICATION_JSON)
            .body(fromValue(Set.of("username=Guest'%0AUser:'Admin")))
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
  void transportMappingIdsAndDateShiftingValuesAndFetchPseudonyms() throws IOException {
    var fhirGenerator =
        gpasGetOrCreateResponse(
            fromList(List.of("id-144218", "Salt_id-144218", "PT336H_id-144218")),
            fromList(List.of("469680023", "123", "12345")));

    List.of("id-144218", "Salt_id-144218", "PT336H_id-144218")
        .forEach(
            key ->
                gpas()
                    .register(
                        post(urlEqualTo("/ttp-fhir/fhir/gpas/$pseudonymizeAllowCreate"))
                            .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON))
                            .withRequestBody(
                                equalToJson(
                                    """
                                    { "resourceType": "Parameters",
                                      "parameter": [
                                        {"name": "target", "valueString": "MII"},
                                        {"name": "original", "valueString": "%s"}]}
                                    """
                                        .formatted(key),
                                    true,
                                    true))
                            .willReturn(fhirResponse(fhirGenerator.generateString(), 200))));

    var transferId =
        doPost(
                ofEntries(
                    entry(
                        "tcaDomains",
                        ofEntries(
                            entry("pseudonym", "MII"),
                            entry("salt", "MII"),
                            entry("dateShift", "MII"))),
                    entry("patientId", "id-144218"),
                    entry("resourceIds", Set.of("id-144218", "id-244194")),
                    entry("maxDateShift", ofDays(14).getSeconds())))
            .block()
            .transferId();

    var response =
        rdClient
            .post()
            .uri("/api/v2/rd/research-mapping")
            .contentType(APPLICATION_JSON)
            .body(fromValue(transferId))
            .accept(APPLICATION_JSON)
            .retrieve()
            .toEntity(ResearchMappingResponse.class);

    create(response)
        .assertNext(
            res -> {
              assertThat(res).isNotNull();
              assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
              var body = res.getBody();
              assertThat(body).isNotNull();
              assertThat(body.dateShiftBy()).isLessThanOrEqualTo(Duration.ofMillis(-470961186L));
            })
        .verifyComplete();
  }

  private static Mono<TransportMappingResponse> doPost(Map<String, Object> body) {
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
