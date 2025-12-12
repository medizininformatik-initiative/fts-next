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
 * <p>Tests the Vfps-compatible endpoint that resolves transport IDs to secure pseudonyms (sIDs).
 */
@Slf4j
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestWebClientFactory.class)
class RdAgentFhirPseudonymizerControllerIT extends BaseIT {

  private static final String VFPS_ENDPOINT = "/api/v2/rd/fhir/$create-pseudonym";

  @Autowired private RedissonClient redisClient;
  @Autowired private TransportIdService transportIdService;
  private WebClient rdClient;

  @BeforeEach
  void setUp(@LocalServerPort int port, @Autowired TestWebClientFactory factory) {
    rdClient = factory.webClient("https://localhost:" + port, "rd-agent");
    // Clean up Redis before each test
    redisClient.getKeys().deleteByPattern("tid:*");
  }

  @AfterEach
  void tearDown() {
    redisClient.getKeys().deleteByPattern("tid:*");
  }

  @Test
  void resolvePseudonyms_shouldReturnSecurePseudonym() {
    // First, store a mapping (simulating what CDA endpoint would do)
    var tId = "test-transport-id-resolve";
    var sId = "secure-pseudonym-final";

    transportIdService.storeMapping(tId, sId, Duration.ofMinutes(5)).block();

    // Build Vfps-format request with the tID
    var requestParams = buildVfpsRequest("test-domain", tId);

    // Send request to resolve
    var response =
        rdClient
            .post()
            .uri(VFPS_ENDPOINT)
            .header(CONTENT_TYPE, APPLICATION_FHIR_JSON)
            .header("Accept", APPLICATION_FHIR_JSON)
            .bodyValue(requestParams)
            .retrieve()
            .bodyToMono(Parameters.class);

    create(response)
        .assertNext(
            params -> {
              assertThat(params).isNotNull();
              // Single value response has 3 flat parameters
              assertThat(params.getParameter()).hasSize(3);

              // Verify the resolved pseudonym is the sID, not the tID
              var pseudonymValue = extractPseudonymValue(params);
              assertThat(pseudonymValue)
                  .isNotNull()
                  .isEqualTo(sId) // Should be the real secure pseudonym
                  .isNotEqualTo(tId); // NOT the transport ID
            })
        .verifyComplete();
  }

  @Test
  void resolvePseudonyms_withUnknownTransportId_shouldReturnOriginal() {
    var unknownTId = "unknown-transport-id";

    var requestParams = buildVfpsRequest("test-domain", unknownTId);

    var response =
        rdClient
            .post()
            .uri(VFPS_ENDPOINT)
            .header(CONTENT_TYPE, APPLICATION_FHIR_JSON)
            .header("Accept", APPLICATION_FHIR_JSON)
            .bodyValue(requestParams)
            .retrieve()
            .bodyToMono(Parameters.class);

    create(response)
        .assertNext(
            params -> {
              assertThat(params).isNotNull();
              // Unknown tID should return the tID itself (not fail)
              var pseudonymValue = extractPseudonymValue(params);
              assertThat(pseudonymValue).isEqualTo(unknownTId);
            })
        .verifyComplete();
  }

  @Test
  void resolvePseudonyms_withMissingNamespace_shouldReturn400() {
    var requestParams = new Parameters();
    requestParams.addParameter().setName("originalValue").setValue(new StringType("some-tid"));

    var response =
        rdClient
            .post()
            .uri(VFPS_ENDPOINT)
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
  void resolvePseudonyms_multipleMappings_shouldResolveAll() {
    // Store multiple mappings
    var domain = "test-domain";
    var ttl = Duration.ofMinutes(5);

    transportIdService.storeMapping("tId-1", "sId-1", ttl).block();
    transportIdService.storeMapping("tId-2", "sId-2", ttl).block();
    transportIdService.storeMapping("tId-3", "sId-3", ttl).block();

    // Build request with multiple tIDs
    var requestParams = new Parameters();
    requestParams.addParameter().setName("namespace").setValue(new StringType(domain));
    requestParams.addParameter().setName("originalValue").setValue(new StringType("tId-1"));
    requestParams.addParameter().setName("originalValue").setValue(new StringType("tId-2"));
    requestParams.addParameter().setName("originalValue").setValue(new StringType("tId-3"));

    var response =
        rdClient
            .post()
            .uri(VFPS_ENDPOINT)
            .header(CONTENT_TYPE, APPLICATION_FHIR_JSON)
            .header("Accept", APPLICATION_FHIR_JSON)
            .bodyValue(requestParams)
            .retrieve()
            .bodyToMono(Parameters.class);

    create(response)
        .assertNext(
            params -> {
              assertThat(params).isNotNull();
              // Should have 3 nested pseudonym entries
              var pseudonymParams =
                  params.getParameter().stream()
                      .filter(p -> "pseudonym".equals(p.getName()))
                      .toList();
              assertThat(pseudonymParams).hasSize(3);
            })
        .verifyComplete();
  }

  private Parameters buildVfpsRequest(String namespace, String transportId) {
    var params = new Parameters();
    params.addParameter().setName("namespace").setValue(new StringType(namespace));
    params.addParameter().setName("originalValue").setValue(new StringType(transportId));
    return params;
  }

  private String extractPseudonymValue(Parameters params) {
    return params.getParameter().stream()
        .filter(p -> "pseudonymValue".equals(p.getName()))
        .findFirst()
        .map(p -> p.getValue().primitiveValue())
        .orElseGet(
            () ->
                params.getParameter().stream()
                    .filter(p -> "pseudonym".equals(p.getName()))
                    .findFirst()
                    .map(this::extractPseudonymValueFromPart)
                    .orElse(null));
  }

  private String extractPseudonymValueFromPart(Parameters.ParametersParameterComponent param) {
    return param.getPart().stream()
        .filter(p -> "pseudonymValue".equals(p.getName()))
        .findFirst()
        .map(p -> p.getValue().primitiveValue())
        .orElse(null);
  }
}
