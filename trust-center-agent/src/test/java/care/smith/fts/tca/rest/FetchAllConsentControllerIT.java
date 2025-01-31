package care.smith.fts.tca.rest;

import static care.smith.fts.test.FhirGenerators.randomUuid;
import static care.smith.fts.test.MockServerUtil.APPLICATION_FHIR_JSON;
import static care.smith.fts.test.MockServerUtil.FIRST;
import static care.smith.fts.test.MockServerUtil.REST;
import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
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
class FetchAllConsentControllerIT extends BaseIT {
  private WebClient client;

  @BeforeEach
  void setUp(@LocalServerPort int port, @Autowired TestWebClientFactory factory) {
    client = factory.webClient("https://localhost:" + port, "cd-agent");
  }

  @Test
  void successfulRequest() throws IOException {
    var consentGenerator = FhirGenerators.gicsResponse(randomUuid(), () -> "FTS001");
    gics()
        .register(
            post(urlPathEqualTo("/ttp-fhir/fhir/gics/$allConsentsForDomain"))
                .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON))
                .willReturn(fhirResponse(consentGenerator.generateString())));

    var response =
        fetchAll(
            ofEntries(
                entry("domain", "MII"),
                entry("policies", Set.of("")),
                entry("policySystem", "sys")));

    create(response)
        .assertNext(
            val -> {
              log.info("Response: {}", val);
              assertThat(val).isNotBlank();
            })
        .verifyComplete();
  }

  private Mono<String> fetchAll(Map<String, Object> body) {
    return client
        .post()
        .uri("/api/v2/cd/consented-patients/fetch-all")
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaTypes.APPLICATION_FHIR_JSON)
        .bodyValue(body)
        .retrieve()
        .bodyToMono(String.class);
  }

  @Test
  void firstRequestToGicsFails() throws IOException {
    var consentGenerator = FhirGenerators.gicsResponse(randomUuid(), () -> "FTS001");

    gics()
        .register(
            post(urlPathEqualTo("/ttp-fhir/fhir/gics/$allConsentsForDomain"))
                .inScenario("firstRequestFails")
                .whenScenarioStateIs(FIRST)
                .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON))
                .willSetStateTo(REST)
                .willReturn(serverError()));
    gics()
        .register(
            post(urlPathEqualTo("/ttp-fhir/fhir/gics/$allConsentsForDomain"))
                .inScenario("firstRequestFails")
                .whenScenarioStateIs(REST)
                .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON))
                .willReturn(fhirResponse(consentGenerator.generateString())));

    var response =
        fetchAll(
            ofEntries(
                entry("domain", "MII"),
                entry("policies", Set.of("")),
                entry("policySystem", "sys")));

    create(response)
        .assertNext(
            val -> {
              log.info("Response: {}", val);
              assertThat(val).isNotBlank();
            })
        .verifyComplete();
  }

  @AfterEach
  void tearDown() {
    gics().resetMappings();
  }
}
