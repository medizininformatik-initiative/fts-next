package care.smith.fts.tca.deidentification;

import static care.smith.fts.test.MockServerUtil.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.FhirUtils.fhirResourceToString;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.common.ContentTypes.CONTENT_TYPE;
import static com.github.tomakehurst.wiremock.matching.UrlPattern.ANY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.I_AM_A_TEAPOT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static reactor.test.StepVerifier.create;

import care.smith.fts.util.error.fhir.FhirException;
import care.smith.fts.util.error.fhir.NoFhirServerException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@SpringBootTest
@WireMockTest
public class GpasClientIT {
  @Autowired WebClient.Builder httpClientBuilder;
  @Autowired MeterRegistry meterRegistry;

  @MockitoBean
  RedissonClient redisClient; // We need to mock the redisClient otherwise the tests won't start

  private static WireMock wireMock;

  GpasClient gpasClient;

  private static final String requestBody =
      """
          {
            "resourceType": "Parameters",
            "parameter": [
              {"name": "target", "valueString": "domain"},
              {"name": "original", "valueString": "id"}
              ]
           }
      """;
  private static final MappingBuilder gpasRequest =
      post(urlPathEqualTo("/$pseudonymizeAllowCreate")).withRequestBody(equalToJson(requestBody));

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntime) {
    var address = wireMockRuntime.getHttpBaseUrl();
    wireMock = wireMockRuntime.getWireMock();
    gpasClient = new GpasClient(httpClientBuilder.baseUrl(address).build(), meterRegistry);
  }

  @AfterEach
  void tearDown() {
    wireMock.resetMappings();
  }

  private static CapabilityStatement gpasMockCapabilityStatement() {
    var capabilities = new CapabilityStatement();
    var rest = capabilities.addRest();
    rest.addOperation().setName("pseudonymizeAllowCreate");
    return capabilities;
  }

  @Test
  void responseIsNotFHIR() {
    wireMock.register(
        post(ANY).willReturn(status(I_AM_A_TEAPOT.value()).withBody("Would you like some milk?")));
    wireMock.register(
        get(urlPathEqualTo("/metadata"))
            .withQueryParam("_elements", equalTo("rest.operation"))
            .willReturn(status(I_AM_A_TEAPOT.value()).withBody("Would you like some milk?")));
    create(gpasClient.fetchOrCreatePseudonyms("domain", "id"))
        .expectError(NoFhirServerException.class)
        .verify();
  }

  @Test
  void serverIsNoGics() {
    wireMock.register(
        gpasRequest.willReturn(
            status(I_AM_A_TEAPOT.value()).withBody("Would you like some milk?")));
    wireMock.register(
        get(urlPathEqualTo("/metadata"))
            .withQueryParam("_elements", equalTo("rest.operation"))
            .willReturn(status(I_AM_A_TEAPOT.value()).withBody("Would you like some milk?")));
    create(gpasClient.fetchOrCreatePseudonyms("domain", "id"))
        .expectError(NoFhirServerException.class)
        .verify();
  }

  private void gpas4xxResponse(
      String body, String message, HttpStatus gpasResponseStatus, HttpStatus returnStatus) {
    wireMock.register(
        gpasRequest.willReturn(
            status(gpasResponseStatus.value())
                .withHeader(CONTENT_TYPE, APPLICATION_FHIR_JSON)
                .withBody(body)));
    wireMock.register(
        get(urlPathEqualTo("/metadata"))
            .withQueryParam("_elements", equalTo("rest.operation"))
            .willReturn(
                status(OK.value())
                    .withHeader(CONTENT_TYPE, APPLICATION_FHIR_JSON)
                    .withBody(fhirResourceToString(gpasMockCapabilityStatement()))));
    create(gpasClient.fetchOrCreatePseudonyms("domain", "id"))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(FhirException.class).hasMessage(message);
              assertThat(((FhirException) error).getStatusCode()).isEqualTo(returnStatus);
            })
        .verify();
  }

  @Test
  void serverIsGpasButDoesNotSendOperationOutcome() throws JsonProcessingException {
    gpas4xxResponse(
        fhirResourceToString(new Bundle()),
        "Unexpected Error: Cannot parse OperationOutcome from gPAS",
        BAD_REQUEST,
        INTERNAL_SERVER_ERROR);
  }

  @Test
  void gpasReturnsBadRequest() {
    gpas4xxResponse(
        fhirResourceToString(new OperationOutcome()),
        "Fehlende oder fehlerhafte Parameter.",
        BAD_REQUEST,
        BAD_REQUEST);
  }

  @Test
  void gpasReturnsUnauthorized() {
    gpas4xxResponse(
        fhirResourceToString(new OperationOutcome()),
        "Invalid gPAS FHIR gateway configuration",
        UNAUTHORIZED,
        INTERNAL_SERVER_ERROR);
  }

  @Test
  void gpasReturnsNotFound() {
    gpas4xxResponse(
        fhirResourceToString(new OperationOutcome()),
        "Parameter mit unbekanntem Inhalt",
        NOT_FOUND,
        NOT_FOUND);
  }

  @Test
  void gpasReturnsUnprocessableEntity() {
    gpas4xxResponse(
        fhirResourceToString(new OperationOutcome()),
        "Fehlende oder falsche Patienten-Attribute.",
        UNPROCESSABLE_ENTITY,
        UNPROCESSABLE_ENTITY);
  }

  @Test
  void responseHasNoOperationOutcome() {
    wireMock.register(
        gpasRequest.willReturn(
            status(UNAUTHORIZED.value())
                .withHeader(CONTENT_TYPE, "text/plain")
                .withBody("Unauthorized")));
    wireMock.register(
        get("/metadata")
            .willReturn(
                status(UNAUTHORIZED.value())
                    .withHeader(CONTENT_TYPE, "text/plain")
                    .withBody("Unauthorized")));
    create(gpasClient.fetchOrCreatePseudonyms("domain", "id"))
        .expectError(NoFhirServerException.class)
        .verify();
  }

  @Test
  void gpasReturns500() {
    wireMock.register(
        post(ANY)
            .willReturn(
                status(INTERNAL_SERVER_ERROR.value())
                    .withBody("what was I supposed to do again?")));
    create(gpasClient.fetchOrCreatePseudonyms("domain", "id"))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(FhirException.class).hasMessage("gPAS kapuut");
              assertThat(((FhirException) error).getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
            })
        .verify();
  }

  @Test
  void noGpasServer() {
    gpasClient =
        new GpasClient(httpClientBuilder.baseUrl("http://does-not-exist").build(), meterRegistry);

    create(gpasClient.fetchOrCreatePseudonyms("domain", "id"))
        .expectError(NoFhirServerException.class)
        .verify();
  }
}
