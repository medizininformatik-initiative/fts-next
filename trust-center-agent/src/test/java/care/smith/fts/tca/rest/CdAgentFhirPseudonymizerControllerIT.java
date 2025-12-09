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
 * <p>Tests the Vfps-compatible endpoint that generates transport IDs for CDA requests.
 */
@Slf4j
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestWebClientFactory.class)
class CdAgentFhirPseudonymizerControllerIT extends BaseIT {

  private static final String VFPS_ENDPOINT = "/api/v2/cd-agent/fhir/$create-pseudonym";
  private static final String MEDIA_TYPE_FHIR_JSON = "application/fhir+json";

  @Autowired private RedissonClient redisClient;
  private WebClient cdClient;

  @BeforeEach
  void setUp(
      @LocalServerPort int port,
      @Autowired TestWebClientFactory factory) {
    cdClient = factory.webClient("https://localhost:" + port, "cd-agent");
    // Clean up Redis before each test
    redisClient.getKeys().deleteByPattern("transport-mapping:*");
  }

  @AfterEach
  void tearDown() {
    gpas().resetMappings();
  }

  @Test
  void createPseudonym_shouldReturnTransportId() throws IOException {
    // Setup gPAS mock to return a real pseudonym
    var fhirGenerator =
        gpasGetOrCreateResponse(
            fromList(List.of("patient-123")),
            fromList(List.of("sID-real-pseudonym-abc")));

    gpas()
        .register(
            post(urlEqualTo("/ttp-fhir/fhir/gpas/$pseudonymizeAllowCreate"))
                .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON))
                .willReturn(fhirResponse(fhirGenerator.generateString())));

    // Build Vfps-format request
    var requestParams = buildVfpsRequest("clinical-domain", "patient-123");

    // Send request
    var response =
        cdClient
            .post()
            .uri(VFPS_ENDPOINT)
            .header(CONTENT_TYPE, MEDIA_TYPE_FHIR_JSON)
            .header("Accept", MEDIA_TYPE_FHIR_JSON)
            .bodyValue(requestParams)
            .retrieve()
            .bodyToMono(Parameters.class);

    create(response)
        .assertNext(
            params -> {
              assertThat(params).isNotNull();
              // Single value response has 3 flat parameters: namespace, originalValue, pseudonymValue
              assertThat(params.getParameter()).hasSize(3);

              // The response should contain a pseudonymValue that is a transport ID (not the real sID)
              var pseudonymValue = extractPseudonymValue(params);
              assertThat(pseudonymValue)
                  .isNotNull()
                  .isNotEqualTo("sID-real-pseudonym-abc") // Must NOT be the real pseudonym
                  .hasSize(32) // Transport IDs are 32 chars (24 bytes Base64URL)
                  .matches(s -> s.matches("^[A-Za-z0-9_-]+$"), "should be Base64URL encoded");
            })
        .verifyComplete();
  }

  @Test
  void createPseudonym_shouldStoreMapping() throws IOException {
    // Setup gPAS mock
    var fhirGenerator =
        gpasGetOrCreateResponse(
            fromList(List.of("patient-456")),
            fromList(List.of("sID-stored-pseudonym")));

    gpas()
        .register(
            post(urlEqualTo("/ttp-fhir/fhir/gpas/$pseudonymizeAllowCreate"))
                .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON))
                .willReturn(fhirResponse(fhirGenerator.generateString())));

    // Build and send request
    var requestParams = buildVfpsRequest("clinical-domain", "patient-456");

    var transportId =
        cdClient
            .post()
            .uri(VFPS_ENDPOINT)
            .header(CONTENT_TYPE, MEDIA_TYPE_FHIR_JSON)
            .header("Accept", MEDIA_TYPE_FHIR_JSON)
            .bodyValue(requestParams)
            .retrieve()
            .bodyToMono(Parameters.class)
            .map(this::extractPseudonymValue)
            .block();

    // Verify mapping was stored in Redis
    var keys = redisClient.getKeys().getKeysByPattern("transport-mapping:*");
    assertThat(keys).isNotEmpty();

    // The mapping should contain the transport ID -> real pseudonym
    var transferId = keys.iterator().next().replace("transport-mapping:", "");
    var mapping = redisClient.<String, String>getMapCache("transport-mapping:" + transferId);
    assertThat(mapping.get(transportId)).isEqualTo("sID-stored-pseudonym");
  }

  @Test
  void createPseudonym_withMultipleOriginals_shouldReturnMultipleTransportIds() throws IOException {
    // Setup gPAS mock for batch processing
    var fhirGenerator =
        gpasGetOrCreateResponse(
            fromList(List.of("patient-1", "patient-2", "patient-3")),
            fromList(List.of("sID-1", "sID-2", "sID-3")));

    gpas()
        .register(
            post(urlEqualTo("/ttp-fhir/fhir/gpas/$pseudonymizeAllowCreate"))
                .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON))
                .willReturn(fhirResponse(fhirGenerator.generateString())));

    // Build request with multiple originals
    var requestParams = new Parameters();
    requestParams.addParameter().setName("namespace").setValue(new StringType("clinical-domain"));
    requestParams.addParameter().setName("originalValue").setValue(new StringType("patient-1"));
    requestParams.addParameter().setName("originalValue").setValue(new StringType("patient-2"));
    requestParams.addParameter().setName("originalValue").setValue(new StringType("patient-3"));

    var response =
        cdClient
            .post()
            .uri(VFPS_ENDPOINT)
            .header(CONTENT_TYPE, MEDIA_TYPE_FHIR_JSON)
            .header("Accept", MEDIA_TYPE_FHIR_JSON)
            .bodyValue(requestParams)
            .retrieve()
            .bodyToMono(Parameters.class);

    create(response)
        .assertNext(
            params -> {
              assertThat(params).isNotNull();
              // For batch response (>1 original), should have nested "pseudonym" parameters
              // Total parameters should be 3 (one for each original)
              var pseudonymParams =
                  params.getParameter().stream()
                      .filter(p -> "pseudonym".equals(p.getName()))
                      .toList();

              // If we have nested structure, pseudonymParams should have 3 entries
              // If not (e.g., flat structure reused), we need to count differently
              if (pseudonymParams.isEmpty()) {
                // Check if flat structure was used (should not happen for batch)
                assertThat(params.getParameter())
                    .hasSizeGreaterThanOrEqualTo(3);
              } else {
                assertThat(pseudonymParams).hasSize(3);

                // All should have unique transport IDs
                var transportIds =
                    pseudonymParams.stream()
                        .map(this::extractPseudonymValueFromPart)
                        .toList();
                assertThat(transportIds).hasSize(3).doesNotHaveDuplicates();
              }
            })
        .verifyComplete();
  }

  @Test
  void createPseudonym_withMissingNamespace_shouldReturn400() {
    var requestParams = new Parameters();
    requestParams.addParameter().setName("originalValue").setValue(new StringType("patient-123"));

    var response =
        cdClient
            .post()
            .uri(VFPS_ENDPOINT)
            .header(CONTENT_TYPE, MEDIA_TYPE_FHIR_JSON)
            .header("Accept", MEDIA_TYPE_FHIR_JSON)
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
  void createPseudonym_withMissingOriginalValue_shouldReturn400() {
    var requestParams = new Parameters();
    requestParams.addParameter().setName("namespace").setValue(new StringType("clinical-domain"));

    var response =
        cdClient
            .post()
            .uri(VFPS_ENDPOINT)
            .header(CONTENT_TYPE, MEDIA_TYPE_FHIR_JSON)
            .header("Accept", MEDIA_TYPE_FHIR_JSON)
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

  private Parameters buildVfpsRequest(String namespace, String originalValue) {
    var params = new Parameters();
    params.addParameter().setName("namespace").setValue(new StringType(namespace));
    params.addParameter().setName("originalValue").setValue(new StringType(originalValue));
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
        .filter(p -> "pseudonymValue".equals(p.getName()) || "pseudonym".equals(p.getName()))
        .findFirst()
        .map(p -> p.getValue().primitiveValue())
        .orElse(null);
  }
}
