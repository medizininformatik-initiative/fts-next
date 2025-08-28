package care.smith.fts.packager.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.packager.config.PseudonymizerConfig;
import care.smith.fts.packager.service.PseudonymizerClient;
import care.smith.fts.packager.service.PseudonymizerClientImpl;
import care.smith.fts.util.fhir.FhirUtils;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Integration tests for PseudonymizerClientImpl using WireMock to simulate the external service.
 * 
 * <p>These tests verify the client's ability to handle various HTTP scenarios including
 * successful responses, error conditions, retry logic, and timeout handling.
 */
class PseudonymizerClientWireMockIT {

  @RegisterExtension
  static WireMockExtension wireMock = WireMockExtension.newInstance()
      .options(wireMockConfig()
          .port(0) // Use random port
          .asynchronousResponseEnabled(true)
          .asynchronousResponseThreads(10))
      .build();

  private PseudonymizerClient client;
  private Bundle testBundle;
  private String testBundleJson;

  @BeforeEach
  void setUp() {
    // Create test configuration pointing to WireMock server
    var retryConfig = new PseudonymizerConfig.RetryConfig(
        3, // maxAttempts
        Duration.ofMillis(50), // initialBackoff - fast for tests
        Duration.ofMillis(500), // maxBackoff - fast for tests
        2.0 // backoffMultiplier
    );
    
    var config = new PseudonymizerConfig(
        "http://localhost:" + wireMock.getPort(),
        Duration.ofMillis(500), // connectTimeout - fast for tests
        Duration.ofMillis(1000), // readTimeout - fast for tests
        retryConfig,
        true // healthCheckEnabled
    );

    // Create client with test configuration
    client = new PseudonymizerClientImpl(config, WebClient.builder());

    // Create test bundle
    testBundle = createTestBundle();
    testBundleJson = FhirUtils.fhirResourceToString(testBundle);

    // Configure WireMock to use the same port
    WireMock.configureFor("localhost", wireMock.getPort());
  }

  @Test
  void pseudonymize_WithSuccessfulResponse_ShouldReturnPseudonymizedBundle() {
    Bundle expectedResponse = createPseudonymizedBundle();
    String responseJson = FhirUtils.fhirResourceToString(expectedResponse);

    stubFor(post(urlEqualTo("/fhir/$de-identify"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/fhir+json")
            .withBody(responseJson)));

    Mono<Bundle> result = client.pseudonymize(testBundle);

    StepVerifier.create(result)
        .assertNext(bundle -> {
          assertThat(bundle).isNotNull();
          assertThat(bundle.getType()).isEqualTo(BundleType.COLLECTION);
          assertThat(bundle.getIdElement().getIdPart()).isEqualTo("pseudonymized-test-bundle");
          assertThat(bundle.getEntry()).hasSize(1);
        })
        .verifyComplete();
  }

  @Test
  void pseudonymize_WithServiceUnavailable_ShouldRetryAndSucceed() {
    Bundle expectedResponse = createPseudonymizedBundle();
    String responseJson = FhirUtils.fhirResourceToString(expectedResponse);

    // First two calls return 503, third succeeds
    stubFor(post(urlEqualTo("/fhir/$de-identify"))
        .inScenario("retry-scenario")
        .whenScenarioStateIs("Started")
        .willReturn(aResponse()
            .withStatus(503)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\":\"Service temporarily unavailable\"}"))
        .willSetStateTo("first-retry"));

    stubFor(post(urlEqualTo("/fhir/$de-identify"))
        .inScenario("retry-scenario")
        .whenScenarioStateIs("first-retry")
        .willReturn(aResponse()
            .withStatus(503)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\":\"Service still unavailable\"}"))
        .willSetStateTo("second-retry"));

    stubFor(post(urlEqualTo("/fhir/$de-identify"))
        .inScenario("retry-scenario")
        .whenScenarioStateIs("second-retry")
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/fhir+json")
            .withBody(responseJson)));

    Mono<Bundle> result = client.pseudonymize(testBundle);

    StepVerifier.create(result)
        .assertNext(bundle -> {
          assertThat(bundle).isNotNull();
          assertThat(bundle.getIdElement().getIdPart()).isEqualTo("pseudonymized-test-bundle");
        })
        .verifyComplete();
  }

  @Test
  void pseudonymize_WithBadRequest_ShouldNotRetryAndFail() {
    stubFor(post(urlEqualTo("/fhir/$de-identify"))
        .willReturn(aResponse()
            .withStatus(400)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\":\"Invalid FHIR Bundle format\"}")));

    Mono<Bundle> result = client.pseudonymize(testBundle);

    StepVerifier.create(result)
        .expectErrorMatches(throwable -> 
            throwable instanceof WebClientResponseException.BadRequest)
        .verify();
  }

  @Test
  void pseudonymize_WithUnauthorized_ShouldNotRetryAndFail() {
    stubFor(post(urlEqualTo("/fhir/$de-identify"))
        .willReturn(aResponse()
            .withStatus(401)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\":\"Authentication required\"}")));

    Mono<Bundle> result = client.pseudonymize(testBundle);

    StepVerifier.create(result)
        .expectErrorMatches(throwable -> 
            throwable instanceof WebClientResponseException.Unauthorized)
        .verify();
  }

  @Test
  void pseudonymize_WithInternalServerError_ShouldRetryAndEventuallyFail() {
    // Given - All retries return 500
    stubFor(post(urlEqualTo("/fhir/$de-identify"))
        .willReturn(aResponse()
            .withStatus(500)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\":\"Internal server error\"}")));

    Mono<Bundle> result = client.pseudonymize(testBundle);

    StepVerifier.create(result)
        .expectErrorMatches(throwable -> 
            throwable instanceof WebClientResponseException.InternalServerError)
        .verify();
  }

  @Test
  void pseudonymize_WithTimeout_ShouldRetryAndEventuallyFail() {
    // Given - Simulate timeout with delayed response
    stubFor(post(urlEqualTo("/fhir/$de-identify"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/fhir+json")
            .withBody(testBundleJson)
            .withFixedDelay(2000))); // 2 second delay, longer than timeout

    Mono<Bundle> result = client.pseudonymize(testBundle);

    StepVerifier.create(result)
        .expectErrorMatches(throwable -> 
            throwable.getCause() instanceof java.util.concurrent.TimeoutException ||
            throwable instanceof org.springframework.web.reactive.function.client.WebClientRequestException)
        .verify();
  }

  @Test
  void pseudonymize_WithLargeBundle_ShouldHandleSuccessfully() {
    Bundle largeBundle = createLargeBundleForTesting();
    String largeBundleJson = FhirUtils.fhirResourceToString(largeBundle);
    
    Bundle expectedResponse = createPseudonymizedBundle();
    expectedResponse.setId("pseudonymized-large-bundle");
    String responseJson = FhirUtils.fhirResourceToString(expectedResponse);

    stubFor(post(urlEqualTo("/fhir/$de-identify"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/fhir+json")
            .withBody(responseJson)));

    Mono<Bundle> result = client.pseudonymize(largeBundle);

    StepVerifier.create(result)
        .assertNext(bundle -> {
          assertThat(bundle).isNotNull();
          assertThat(bundle.getIdElement().getIdPart()).isEqualTo("pseudonymized-large-bundle");
        })
        .verifyComplete();
  }

  @Test
  void checkHealth_WithHealthyService_ShouldReturnHealthyStatus() {
    stubFor(get(urlEqualTo("/fhir/metadata"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/fhir+json")
            .withBody("{\"resourceType\":\"CapabilityStatement\",\"status\":\"active\"}")));

    Mono<PseudonymizerClient.HealthStatus> result = client.checkHealth();

    StepVerifier.create(result)
        .assertNext(status -> {
          assertThat(status.healthy()).isTrue();
          assertThat(status.message()).isEqualTo("Service is healthy");
          assertThat(status.responseTimeMs()).isGreaterThan(0);
        })
        .verifyComplete();
  }

  @Test
  void checkHealth_WithUnhealthyService_ShouldReturnUnhealthyStatus() {
    stubFor(get(urlEqualTo("/fhir/metadata"))
        .willReturn(aResponse()
            .withStatus(503)
            .withHeader("Content-Type", "application/json")
            .withBody("{\"error\":\"Service unavailable\"}")));

    Mono<PseudonymizerClient.HealthStatus> result = client.checkHealth();

    StepVerifier.create(result)
        .assertNext(status -> {
          assertThat(status.healthy()).isFalse();
          assertThat(status.message()).contains("Service unavailable");
        })
        .verifyComplete();
  }

  @Test
  void pseudonymize_WithNullBundle_ShouldFailImmediately() {
    Mono<Bundle> result = client.pseudonymize(null);

    StepVerifier.create(result)
        .expectErrorMatches(throwable -> 
            throwable instanceof IllegalArgumentException &&
            throwable.getMessage().contains("Bundle cannot be null"))
        .verify();
  }

  private Bundle createTestBundle() {
    Bundle bundle = new Bundle();
    bundle.setId("test-bundle");
    bundle.setType(BundleType.COLLECTION);

    Patient patient = new Patient();
    patient.setId("test-patient");
    patient.addName().setFamily("Doe").addGiven("John");
    patient.setBirthDateElement(new org.hl7.fhir.r4.model.DateType("1990-01-01"));

    bundle.addEntry().setResource(patient);
    return bundle;
  }

  private Bundle createPseudonymizedBundle() {
    Bundle bundle = new Bundle();
    bundle.setId("pseudonymized-test-bundle");
    bundle.setType(BundleType.COLLECTION);

    Patient patient = new Patient();
    patient.setId("pseudonymized-patient");
    patient.addName().setFamily("***").addGiven("***"); // Pseudonymized name
    patient.setBirthDateElement(new org.hl7.fhir.r4.model.DateType("1990-01-01"));

    bundle.addEntry().setResource(patient);
    return bundle;
  }

  private Bundle createLargeBundleForTesting() {
    Bundle bundle = new Bundle();
    bundle.setId("large-test-bundle");
    bundle.setType(BundleType.COLLECTION);

    // Add multiple patients to simulate a larger bundle
    for (int i = 0; i < 50; i++) {
      Patient patient = new Patient();
      patient.setId("patient-" + i);
      patient.addName().setFamily("Family" + i).addGiven("Given" + i);
      patient.setBirthDateElement(new org.hl7.fhir.r4.model.DateType("1990-01-01"));
      
      bundle.addEntry().setResource(patient);
    }
    
    return bundle;
  }
}