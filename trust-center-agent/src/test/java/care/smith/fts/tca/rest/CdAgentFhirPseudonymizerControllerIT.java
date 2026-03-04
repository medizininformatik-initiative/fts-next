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

import care.smith.fts.tca.BaseIT;
import care.smith.fts.test.TestWebClientFactory;
import java.io.IOException;
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
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Integration tests for CdAgentFhirPseudonymizerController.
 *
 * <p>Tests the MII $pseudonymize endpoint that generates transport IDs for CDA requests.
 */
@Slf4j
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestWebClientFactory.class)
class CdAgentFhirPseudonymizerControllerIT extends BaseIT {

  private static final String PSEUDONYMIZE_ENDPOINT = "/api/v2/cd/fhir/$pseudonymize";

  @Autowired private RedissonClient redisClient;
  private WebClient cdClient;

  @BeforeEach
  void setUp(@LocalServerPort int port, @Autowired TestWebClientFactory factory) {
    cdClient = factory.webClient("https://localhost:" + port, "cd-agent");
    redisClient.getKeys().deleteByPattern("transport-mapping:*");
  }

  @AfterEach
  void tearDown() {
    gpas().resetMappings();
  }

  @Test
  void pseudonymize_shouldReturnTransportId() throws IOException {
    var fhirGenerator =
        gpasGetOrCreateResponse(
            fromList(List.of("patient-123")), fromList(List.of("sID-real-pseudonym-abc")));

    gpas()
        .register(
            post(urlEqualTo("/ttp-fhir/fhir/gpas/$pseudonymizeAllowCreate"))
                .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON))
                .willReturn(fhirResponse(fhirGenerator.generateString())));

    var requestParams = buildMiiRequest("clinical-domain", "patient-123");

    var response =
        cdClient
            .post()
            .uri(PSEUDONYMIZE_ENDPOINT)
            .header(CONTENT_TYPE, APPLICATION_FHIR_JSON)
            .header("Accept", APPLICATION_FHIR_JSON)
            .bodyValue(requestParams)
            .retrieve()
            .bodyToMono(Parameters.class);

    create(response)
        .assertNext(
            params -> {
              assertThat(params).isNotNull();
              assertThat(params.getParameter()).hasSize(1);

              var pseudonym = findParameterValue(params, "pseudonym");
              assertThat(pseudonym)
                  .isNotNull()
                  .isNotEqualTo("sID-real-pseudonym-abc")
                  .hasSize(32)
                  .matches(s -> s.matches("^[A-Za-z0-9_-]+$"), "should be Base64URL encoded");
            })
        .verifyComplete();
  }

  @Test
  void pseudonymize_shouldStoreMapping() throws IOException {
    var fhirGenerator =
        gpasGetOrCreateResponse(
            fromList(List.of("patient-456")), fromList(List.of("sID-stored-pseudonym")));

    gpas()
        .register(
            post(urlEqualTo("/ttp-fhir/fhir/gpas/$pseudonymizeAllowCreate"))
                .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON))
                .willReturn(fhirResponse(fhirGenerator.generateString())));

    var requestParams = buildMiiRequest("clinical-domain", "patient-456");

    var transportId =
        cdClient
            .post()
            .uri(PSEUDONYMIZE_ENDPOINT)
            .header(CONTENT_TYPE, APPLICATION_FHIR_JSON)
            .header("Accept", APPLICATION_FHIR_JSON)
            .bodyValue(requestParams)
            .retrieve()
            .bodyToMono(Parameters.class)
            .map(params -> findParameterValue(params, "pseudonym"))
            .block();

    var keys = redisClient.getKeys().getKeysByPattern("tid:*");
    assertThat(keys).isNotEmpty();

    var storedSid = redisClient.<String>getBucket("tid:" + transportId).get();
    assertThat(storedSid).isEqualTo("sID-stored-pseudonym");
  }

  @Test
  void pseudonymize_withMissingTarget_shouldReturn400() {
    var requestParams = new Parameters();
    requestParams.addParameter().setName("original").setValue(new StringType("patient-123"));

    var response =
        cdClient
            .post()
            .uri(PSEUDONYMIZE_ENDPOINT)
            .header(CONTENT_TYPE, APPLICATION_FHIR_JSON)
            .header("Accept", APPLICATION_FHIR_JSON)
            .bodyValue(requestParams)
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
  void pseudonymize_withMissingOriginal_shouldReturn400() {
    var requestParams = new Parameters();
    requestParams.addParameter().setName("target").setValue(new StringType("clinical-domain"));

    var response =
        cdClient
            .post()
            .uri(PSEUDONYMIZE_ENDPOINT)
            .header(CONTENT_TYPE, APPLICATION_FHIR_JSON)
            .header("Accept", APPLICATION_FHIR_JSON)
            .bodyValue(requestParams)
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

  private Parameters buildMiiRequest(String target, String original) {
    var params = new Parameters();
    params.addParameter().setName("target").setValue(new StringType(target));
    params.addParameter().setName("original").setValue(new StringType(original));
    return params;
  }

  private String findParameterValue(Parameters params, String name) {
    return params.getParameter().stream()
        .filter(p -> name.equals(p.getName()))
        .findFirst()
        .map(p -> p.getValue().primitiveValue())
        .orElse(null);
  }
}
