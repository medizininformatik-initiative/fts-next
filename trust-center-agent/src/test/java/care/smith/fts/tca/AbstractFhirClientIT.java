package care.smith.fts.tca;

import static care.smith.fts.test.MockServerUtil.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.FhirUtils.fhirResourceToString;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
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
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

import care.smith.fts.util.error.fhir.FhirException;
import care.smith.fts.util.error.fhir.NoFhirServerException;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Abstract base class for FHIR client integration tests.
 *
 * @param <T> The client type being tested
 * @param <R> The client request type
 * @param <S> The client response type
 */
@Slf4j
@WireMockTest
public abstract class AbstractFhirClientIT<T, R, S> {

  protected WebClient.Builder httpClientBuilder;
  protected MeterRegistry meterRegistry;

  protected WireMock wireMock;
  protected T client;

  /**
   * Initialize the abstract test class with needed dependencies.
   *
   * @param httpClientBuilder the WebClient builder
   * @param meterRegistry the metric registry
   */
  protected void init(WebClient.Builder httpClientBuilder, MeterRegistry meterRegistry) {
    this.httpClientBuilder = httpClientBuilder;
    this.meterRegistry = meterRegistry;
  }

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntime) {
    log.info("Setting up test environment");
    String address = wireMockRuntime.getHttpBaseUrl();
    wireMock = wireMockRuntime.getWireMock();
    client = createClient(address);
  }

  /** Clean up after each test. */
  @AfterEach
  void tearDown() {
    log.info("Tearing down test environment");
    wireMock.resetMappings();
  }

  /**
   * Create the client to be tested.
   *
   * @param baseUrl the base URL for the client
   * @return the client instance
   */
  protected abstract T createClient(String baseUrl);

  /**
   * Get the request mapping builder for the client.
   *
   * @return the mapping builder
   */
  protected abstract MappingBuilder getRequestMappingBuilder();

  /**
   * Get a capability statement for the FHIR server.
   *
   * @return the capability statement
   */
  protected abstract CapabilityStatement getMockCapabilityStatement();

  /**
   * Execute a test request.
   *
   * @param request the request to execute
   * @return a Mono representing the response
   */
  protected abstract Mono<S> executeRequest(R request);

  /**
   * Get the name of the FHIR server.
   *
   * @return the server name
   */
  protected abstract String getServerName();

  /**
   * Get the default request object.
   *
   * @return the default request
   */
  protected abstract R getDefaultRequest();

  /** Test that a server error is handled correctly. */
  @Test
  void serverReturnsBadRequest() {
    setupErrorResponse(
        fhirResourceToString(new OperationOutcome()),
        "Missing or faulty parameters. This should not happen",
        BAD_REQUEST,
        INTERNAL_SERVER_ERROR);
  }

  /** Test that an unauthorized response is handled correctly. */
  @Test
  void serverReturnsUnauthorized() {
    setupErrorResponse(
        fhirResourceToString(new OperationOutcome()),
        "Invalid " + getServerName() + " FHIR gateway configuration",
        UNAUTHORIZED,
        SERVICE_UNAVAILABLE);
  }

  /** Test that a not found response is handled correctly. */
  @Test
  void serverReturnsNotFound() {
    setupErrorResponse(
        fhirResourceToString(new OperationOutcome()),
        getServerName() + " domain not found",
        NOT_FOUND,
        NOT_FOUND);
  }

  /** Test that an unprocessable entity response is handled correctly. */
  @Test
  void serverReturnsUnprocessableEntity() {
    setupErrorResponse(
        fhirResourceToString(new OperationOutcome()),
        "Missing or incorrect patient attributes",
        UNPROCESSABLE_ENTITY,
        UNPROCESSABLE_ENTITY);
  }

  /** */
  @Test
  void serverReturnsUnsupportedStatusCode() {
    setupErrorResponse(
        fhirResourceToString(new OperationOutcome()),
        "Unknown Error. This should not happen",
        I_AM_A_TEAPOT,
        INTERNAL_SERVER_ERROR);
  }

  /** Test that a server that returns a non-operation outcome for error is handled correctly. */
  @Test
  void serverDoesNotSendOperationOutcome() {
    setupErrorResponse(
        fhirResourceToString(new Bundle()),
        "Unexpected Error: Cannot parse OperationOutcome from " + getServerName(),
        BAD_REQUEST,
        INTERNAL_SERVER_ERROR);
  }

  /** Test that a server that returns a non-FHIR response is handled correctly. */
  @Test
  void responseHasNoOperationOutcome() {
    wireMock.register(
        getRequestMappingBuilder()
            .willReturn(
                status(UNAUTHORIZED.value())
                    .withHeader(CONTENT_TYPE, "text/plain")
                    .withBody("Unauthorized")));
    wireMock.register(
        get("/metadata")
            .willReturn(
                status(UNAUTHORIZED.value())
                    .withHeader(CONTENT_TYPE, "text/plain")
                    .withBody("Unauthorized")));
    StepVerifier.create(executeRequest(getDefaultRequest()))
        .expectError(NoFhirServerException.class)
        .verify();
  }

  /** Test that a server that returns a 500 error is handled correctly. */
  @Test
  void serverReturns500() {
    wireMock.register(
        post(ANY)
            .willReturn(
                status(INTERNAL_SERVER_ERROR.value())
                    .withBody("what was I supposed to do again?")));
    StepVerifier.create(executeRequest(getDefaultRequest()))
        .expectErrorSatisfies(
            error -> {
              assertThat(error)
                  .isInstanceOf(FhirException.class)
                  .hasMessage("Unknown " + getServerName() + " error");
              assertThat(((FhirException) error).getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
            })
        .verify();
  }

  /** Test that a non-existent server is handled correctly. */
  @Test
  void noServer() {
    T nonExistentClient = createClient("http://does-not-exist");
    Mono<S> result = executeRequestWithClient(nonExistentClient, getDefaultRequest());
    StepVerifier.create(result).expectError(NoFhirServerException.class).verify();
  }

  /**
   * Execute a request with a specific client.
   *
   * @param specificClient the client to use
   * @param request the request to execute
   * @return a Mono representing the response
   */
  protected abstract Mono<S> executeRequestWithClient(T specificClient, R request);

  /** Test that a non-FHIR server is handled correctly. */
  @Test
  void serverIsNotFhir() {
    wireMock.register(
        getRequestMappingBuilder()
            .willReturn(status(I_AM_A_TEAPOT.value()).withBody("Would you like some milk?")));
    wireMock.register(
        get(urlPathEqualTo("/metadata"))
            .withQueryParam("_elements", equalTo("rest.operation"))
            .willReturn(status(I_AM_A_TEAPOT.value()).withBody("Would you like some milk?")));
    StepVerifier.create(executeRequest(getDefaultRequest()))
        .expectError(NoFhirServerException.class)
        .verify();
  }

  /**
   * Set up a mock error response.
   *
   * @param body the response body
   * @param message the expected error message
   * @param serverResponseStatus the status to return from the server
   * @param expectedClientStatus the status expected in the client exception
   */
  protected void setupErrorResponse(
      String body,
      String message,
      HttpStatus serverResponseStatus,
      HttpStatus expectedClientStatus) {
    wireMock.register(
        getRequestMappingBuilder()
            .willReturn(
                status(serverResponseStatus.value())
                    .withHeader(CONTENT_TYPE, APPLICATION_FHIR_JSON)
                    .withBody(body)));
    wireMock.register(
        get(urlPathEqualTo("/metadata"))
            .withQueryParam("_elements", equalTo("rest.operation"))
            .willReturn(
                status(OK.value())
                    .withHeader(CONTENT_TYPE, APPLICATION_FHIR_JSON)
                    .withBody(fhirResourceToString(getMockCapabilityStatement()))));
    StepVerifier.create(executeRequest(getDefaultRequest()))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(FhirException.class).hasMessage(message);
              assertThat(((FhirException) error).getStatusCode()).isEqualTo(expectedClientStatus);
            })
        .verify();
  }
}
