package care.smith.fts.tca.rest;

import static care.smith.fts.test.FhirGenerators.randomUuid;
import static care.smith.fts.test.MockServerUtil.APPLICATION_FHIR_JSON;
import static care.smith.fts.test.MockServerUtil.FIRST;
import static care.smith.fts.test.MockServerUtil.REST;
import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static reactor.test.StepVerifier.create;

import care.smith.fts.tca.BaseIT;
import care.smith.fts.test.FhirGenerators;
import care.smith.fts.test.TestWebClientFactory;
import care.smith.fts.util.MediaTypes;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

  private static final Map<String, Object> FETCH_CONSENTED_PATIENTS_BODY =
      ofEntries(
          entry("domain", "MII"),
          entry("policies", Set.of("")),
          entry("policySystem", "sys"),
          entry("patientIdentifierSystem", "sys"),
          entry("identifiers", List.of("FTS001")));

  private WebClient client;

  @BeforeEach
  void setUp(@LocalServerPort int port, @Autowired TestWebClientFactory factory) {
    client = factory.webClient("https://localhost:" + port, "cd-agent");
  }

  @Test
  void successfulRequest() throws IOException {
    var consentGenerator =
        FhirGenerators.gicsResponse(randomUuid(), () -> "FTS001", () -> "patient-1");
    gics()
        .register(
            post("/ttp-fhir/fhir/gics/$allConsentsForPerson")
                .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON))
                .willReturn(fhirResponse(consentGenerator.generateString())));

    create(fetch(FETCH_CONSENTED_PATIENTS_BODY))
        .assertNext(val -> assertThat(val).isNotBlank())
        .verifyComplete();
  }

  private Mono<String> fetch(Map<String, Object> body) {
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
    var consentGenerator =
        FhirGenerators.gicsResponse(randomUuid(), () -> "FTS001", () -> "patient-1");

    gics()
        .register(
            post("/ttp-fhir/fhir/gics/$allConsentsForPerson")
                .inScenario("firstRequestFails")
                .whenScenarioStateIs(FIRST)
                .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON))
                .willSetStateTo(REST)
                .willReturn(serverError()));
    gics()
        .register(
            post("/ttp-fhir/fhir/gics/$allConsentsForPerson")
                .inScenario("firstRequestFails")
                .whenScenarioStateIs(REST)
                .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON))
                .willReturn(fhirResponse(consentGenerator.generateString())));

    create(fetch(FETCH_CONSENTED_PATIENTS_BODY))
        .assertNext(val -> assertThat(val).isNotBlank())
        .verifyComplete();
  }

  @AfterEach
  void tearDown() {
    gics().resetMappings();
  }
}
