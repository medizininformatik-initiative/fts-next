package care.smith.fts.tca.rest;

import static care.smith.fts.test.FhirGenerators.randomUuid;
import static care.smith.fts.test.MockServerUtil.APPLICATION_FHIR_JSON;
import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static reactor.test.StepVerifier.create;

import care.smith.fts.tca.BaseIT;
import care.smith.fts.test.FhirGenerators;
import care.smith.fts.test.TestWebClientFactory;
import care.smith.fts.util.MediaTypes;
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
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestWebClientFactory.class)
class FetchConsentControllerIT extends BaseIT {
  private static WebClient client;

  @BeforeAll
  static void setUp(@LocalServerPort int port, @Autowired TestWebClientFactory factory) {
    client = factory.webClient("https://localhost:" + port, "cd-agent");
  }

  @Test
  void successfulRequest() throws IOException {
    var consentGenerator = FhirGenerators.gicsResponse(randomUuid(), () -> "FTS001");
    gics.when(
            request()
                .withMethod("POST")
                .withPath("/ttp-fhir/fhir/gics/$allConsentsForPerson")
                .withContentType(APPLICATION_FHIR_JSON))
        .respond(
            response()
                .withContentType(APPLICATION_FHIR_JSON)
                .withBody(consentGenerator.generateString()));

    checkResponse();
  }

  private static void checkResponse() {

    var response =
        fetch(
            ofEntries(
                entry("domain", "MII"),
                entry("policies", Set.of("")),
                entry("policySystem", "sys"),
                entry("patientIdentifierSystem", "sys"),
                entry("pids", List.of("FTS001"))));
    create(response)
        .assertNext(
            val -> {
              log.info("Response: {}", val);
              assertThat(val).isNotBlank();
            })
        .verifyComplete();
  }

  private static Mono<String> fetch(Map<String, Object> body) {
    return client
        .post()
        .uri("/api/v2/cd/consented-patients/fetch")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaTypes.APPLICATION_FHIR_JSON)
        .bodyValue(body)
        .retrieve()
        .bodyToMono(String.class);
  }

  @Test
  void firstRequestToGicsFails() throws IOException {
    var consentGenerator = FhirGenerators.gicsResponse(randomUuid(), () -> "FTS001");
    var statusCodes = new LinkedList<>(List.of(500));

    gics.when(
            request()
                .withMethod("POST")
                .withPath("/ttp-fhir/fhir/gics/$allConsentsForPerson")
                .withContentType(APPLICATION_FHIR_JSON))
        .respond(
            request ->
                Optional.ofNullable(statusCodes.poll())
                    .map(
                        statusCode ->
                            statusCode < 400
                                ? response()
                                    .withStatusCode(statusCode)
                                    .withContentType(APPLICATION_FHIR_JSON)
                                    .withBody(consentGenerator.generateString())
                                : response().withStatusCode(statusCode))
                    .orElseGet(
                        () ->
                            response()
                                .withStatusCode(200)
                                .withContentType(APPLICATION_FHIR_JSON)
                                .withBody(consentGenerator.generateString())));

    checkResponse();
  }

  @AfterEach
  void tearDown() {
    gics.reset();
  }
}
