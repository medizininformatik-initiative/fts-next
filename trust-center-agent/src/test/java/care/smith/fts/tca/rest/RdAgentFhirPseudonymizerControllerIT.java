package care.smith.fts.tca.rest;

import static care.smith.fts.test.MockServerUtil.APPLICATION_FHIR_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static reactor.test.StepVerifier.create;

import care.smith.fts.tca.BaseIT;
import care.smith.fts.tca.services.TransportIdService;
import care.smith.fts.test.TestWebClientFactory;
import java.time.Duration;
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
 * Integration tests for RdAgentFhirPseudonymizerController.
 *
 * <p>Tests the MII $de-pseudonymize endpoint that resolves transport IDs to secure pseudonyms.
 */
@Slf4j
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestWebClientFactory.class)
class RdAgentFhirPseudonymizerControllerIT extends BaseIT {

  private static final String DE_PSEUDONYMIZE_ENDPOINT = "/api/v2/rd/fhir/$de-pseudonymize";

  @Autowired private RedissonClient redisClient;
  @Autowired private TransportIdService transportIdService;
  private WebClient rdClient;

  @BeforeEach
  void setUp(@LocalServerPort int port, @Autowired TestWebClientFactory factory) {
    rdClient = factory.webClient("https://localhost:" + port, "rd-agent");
    redisClient.getKeys().deleteByPattern("tid:*");
  }

  @AfterEach
  void tearDown() {
    redisClient.getKeys().deleteByPattern("tid:*");
  }

  @Test
  void dePseudonymize_shouldReturnSecurePseudonym() {
    var tId = "test-transport-id-resolve";
    var sId = "secure-pseudonym-final";

    transportIdService.storeMapping(tId, sId, Duration.ofMinutes(5)).block();

    var requestParams = buildMiiRequest("test-domain", tId);

    var response =
        rdClient
            .post()
            .uri(DE_PSEUDONYMIZE_ENDPOINT)
            .header(CONTENT_TYPE, APPLICATION_FHIR_JSON)
            .header("Accept", APPLICATION_FHIR_JSON)
            .bodyValue(requestParams)
            .retrieve()
            .bodyToMono(Parameters.class);

    create(response)
        .assertNext(
            params -> {
              assertThat(params).isNotNull();

              var originalValue = extractOriginalValue(params);
              assertThat(originalValue).isNotNull().isEqualTo(sId).isNotEqualTo(tId);
            })
        .verifyComplete();
  }

  @Test
  void dePseudonymize_withUnknownTransportId_shouldReturnOriginal() {
    var unknownTId = "unknown-transport-id";

    var requestParams = buildMiiRequest("test-domain", unknownTId);

    var response =
        rdClient
            .post()
            .uri(DE_PSEUDONYMIZE_ENDPOINT)
            .header(CONTENT_TYPE, APPLICATION_FHIR_JSON)
            .header("Accept", APPLICATION_FHIR_JSON)
            .bodyValue(requestParams)
            .retrieve()
            .bodyToMono(Parameters.class);

    create(response)
        .assertNext(
            params -> {
              assertThat(params).isNotNull();
              var originalValue = extractOriginalValue(params);
              assertThat(originalValue).isEqualTo(unknownTId);
            })
        .verifyComplete();
  }

  @Test
  void dePseudonymize_withMissingTarget_shouldReturn400() {
    var requestParams = new Parameters();
    requestParams.addParameter().setName("pseudonym").setValue(new StringType("some-tid"));

    var response =
        rdClient
            .post()
            .uri(DE_PSEUDONYMIZE_ENDPOINT)
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

  private Parameters buildMiiRequest(String target, String pseudonym) {
    var params = new Parameters();
    params.addParameter().setName("target").setValue(new StringType(target));
    params.addParameter().setName("pseudonym").setValue(new StringType(pseudonym));
    return params;
  }

  /** Extracts the value from MII $de-pseudonymize response: original -> part[value] */
  private String extractOriginalValue(Parameters params) {
    return params.getParameter().stream()
        .filter(p -> "original".equals(p.getName()))
        .findFirst()
        .flatMap(
            p ->
                p.getPart().stream()
                    .filter(part -> "value".equals(part.getName()))
                    .findFirst()
                    .map(part -> part.getValue().primitiveValue()))
        .orElse(null);
  }
}
