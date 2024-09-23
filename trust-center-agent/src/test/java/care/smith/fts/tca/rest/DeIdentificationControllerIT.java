package care.smith.fts.tca.rest;

import static care.smith.fts.test.GpasTestHelper.pseudonymizeAllowCreate;
import static care.smith.fts.test.MockServerUtil.APPLICATION_FHIR_JSON;
import static java.time.Duration.ofDays;
import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.web.reactive.function.BodyInserters.fromValue;
import static reactor.test.StepVerifier.create;

import care.smith.fts.tca.BaseIT;
import care.smith.fts.test.TestWebClientFactory;
import care.smith.fts.util.tca.PseudonymizeResponse;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.http.MediaType;
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
    cdClient = factory.webClient("cd-agent").baseUrl("https://localhost:" + port).build();
    rdClient = factory.webClient("rd-agent").baseUrl("https://localhost:" + port).build();
  }

  @Test
  void successfulRequest() throws IOException {
    gpas.when(
            request()
                .withMethod("POST")
                .withPath("/ttp-fhir/fhir/gpas/$pseudonymizeAllowCreate")
                .withContentType(APPLICATION_FHIR_JSON))
        .respond(
            response()
                .withContentType(APPLICATION_FHIR_JSON)
                .withBody(
                    pseudonymizeAllowCreate(
                        "MII",
                        ofEntries(
                            entry("id-144218", "pseudonym-144218"),
                            entry("id-244194", "pseudonym-244194")))));

    var response =
        doPost(
            ofEntries(
                entry("domain", "MII"),
                entry("patientId", "id-144218"),
                entry("ids", Set.of("id-144218", "id-244194")),
                entry("dateShift", ofDays(14).getSeconds())));

    create(response)
        .assertNext(
            res -> {
              assertThat(res).isNotNull();
              assertThat(res.dateShiftValue().toDays()).isBetween(-14L, 14L);
              assertThat(res.originalToTransportIDMap()).containsKeys("id-144218", "id-244194");
            })
        .verifyComplete();
  }

  @Test
  void firstRequestToGpasFails() throws IOException {
    var statusCodes = new LinkedList<>(List.of(500));
    String responseBody =
        pseudonymizeAllowCreate(
            "MII",
            ofEntries(
                entry("id-144218", "pseudonym-144218"), entry("id-244194", "pseudonym-244194")));
    gpas.when(
            request()
                .withMethod("POST")
                .withPath("/ttp-fhir/fhir/gpas/$pseudonymizeAllowCreate")
                .withContentType(APPLICATION_FHIR_JSON))
        .respond(
            request ->
                Optional.ofNullable(statusCodes.poll())
                    .filter(statusCode -> statusCode >= 400)
                    .map(statusCode -> response().withStatusCode(statusCode))
                    .orElseGet(
                        () ->
                            response()
                                .withStatusCode(200)
                                .withContentType(APPLICATION_FHIR_JSON)
                                .withBody(responseBody)));

    var response =
        doPost(
            ofEntries(
                entry("domain", "MII"),
                entry("patientId", "id-144218"),
                entry("ids", Set.of("id-144218", "id-244194")),
                entry("dateShift", ofDays(14).getSeconds())));

    create(response)
        .assertNext(
            res -> {
              assertThat(res).isNotNull();
              assertThat(res.dateShiftValue().toDays()).isBetween(-14L, 14L);
              assertThat(res.originalToTransportIDMap()).containsKeys("id-144218", "id-244194");
            })
        .verifyComplete();
  }

  @Test
  void rejectInvalidIds() {
    var response =
        rdClient
            .post()
            .uri("/api/v2/rd/resolve-pseudonyms")
            .contentType(MediaType.APPLICATION_JSON)
            .body(fromValue(Set.of("username=Guest'%0AUser:'Admin")))
            .accept(MediaType.APPLICATION_JSON)
            .retrieve()
            .toBodilessEntity();

    create(response)
        .expectErrorSatisfies(
            e -> {
              assertThat(e).isInstanceOf(WebClientResponseException.class);
              assertThat(((WebClientResponseException) e).getStatusCode())
                  .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            })
        .verify();
  }

  private static Mono<PseudonymizeResponse> doPost(Map<String, Object> body) {
    return cdClient
        .post()
        .uri("/api/v2/cd/transport-ids-and-date-shifting-values")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .retrieve()
        .bodyToMono(PseudonymizeResponse.class);
  }

  @AfterEach
  void tearDown() {
    gics.reset();
  }
}
