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
import static reactor.test.StepVerifier.create;

import care.smith.fts.tca.BaseIT;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@SpringBootTest(webEnvironment = RANDOM_PORT)
class DeIdentificationControllerIT extends BaseIT {

  private static WebClient client;

  @BeforeAll
  static void setUp(@LocalServerPort int port) {
    client = WebClient.builder().baseUrl("http://localhost:" + port).build();
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
                            entry("patient-144218", "pseudonym-144352"),
                            entry("id-144218", "pseudonym-144218"),
                            entry("id-244194", "pseudonym-244194")))));

    var response =
        doPost(
            ofEntries(
                entry("domain", "MII"),
                entry("patientId", "patient-144218"),
                entry("ids", Set.of("id-144218", "id-244194")),
                entry("dateShift", ofDays(14).getSeconds())));

    create(response)
        .assertNext(
            res -> {
              assertThat(res).isNotNull();
              assertThat(res.dateShiftValue().toDays()).isBetween(-14L, 14L);
              assertThat(res.idMap()).containsKeys("id-144218", "id-244194");
            })
        .verifyComplete();
  }

  @Test
  void firstRequestToGpasFails() throws IOException {
    var statusCodes = new LinkedList<>(List.of(500));
    String body =
        pseudonymizeAllowCreate(
            "MII",
            ofEntries(
                entry("patient-144218", "pseudonym-144352"),
                entry("id-144218", "pseudonym-144218"),
                entry("id-244194", "pseudonym-244194")));
    gpas.when(
            request()
                .withMethod("POST")
                .withPath("/ttp-fhir/fhir/gpas/$pseudonymizeAllowCreate")
                .withContentType(APPLICATION_FHIR_JSON))
        .respond(
            request ->
                Optional.ofNullable(statusCodes.poll())
                    .map(
                        statusCode ->
                            statusCode < 400
                                ? response()
                                    .withStatusCode(200)
                                    .withContentType(APPLICATION_FHIR_JSON)
                                    .withBody(body)
                                : response().withStatusCode(statusCode))
                    .orElseGet(
                        () ->
                            response()
                                .withStatusCode(200)
                                .withContentType(APPLICATION_FHIR_JSON)
                                .withBody(body)));

    var response =
        doPost(
            ofEntries(
                entry("domain", "MII"),
                entry("patientId", "patient-144218"),
                entry("ids", Set.of("id-144218", "id-244194")),
                entry("dateShift", ofDays(14).getSeconds())));

    create(response)
        .assertNext(
            res -> {
              assertThat(res).isNotNull();
              assertThat(res.dateShiftValue().toDays()).isBetween(-14L, 14L);
              assertThat(res.idMap()).containsKeys("id-144218", "id-244194");
            })
        .verifyComplete();
  }

  private static Mono<PseudonymizeResponse> doPost(Map<String, Object> body) {
    return client
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
