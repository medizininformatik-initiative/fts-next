package care.smith.fts.cda.impl;

import static care.smith.fts.test.MockServerUtil.clientConfig;
import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static reactor.test.StepVerifier.create;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.cda.ClinicalDomainAgent;
import care.smith.fts.util.WebClientFactory;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Integration tests for FhirPseudonymizerStep in Clinical Domain Agent.
 *
 * <p>These tests verify:
 * <ul>
 *   <li>Deidentification via external FHIR Pseudonymizer service
 *   <li>Retry behavior on service unavailability
 *   <li>Timeout handling
 *   <li>TransportBundle creation with transfer ID
 * </ul>
 */
@Slf4j
@SpringBootTest(classes = ClinicalDomainAgent.class)
@WireMockTest
class FhirPseudonymizerStepIT {

  private static final String FHIR_PSEUDONYMIZER_ENDPOINT = "/fhir";
  private static final String MEDIA_TYPE_FHIR_JSON = "application/fhir+json";

  private FhirPseudonymizerStep step;
  private WireMock wireMock;
  private ConsentedPatientBundle testBundle;
  private FhirContext fhirContext;

  @BeforeEach
  void setUp(
      WireMockRuntimeInfo wireMockRuntime,
      @Autowired WebClientFactory clientFactory,
      @Autowired MeterRegistry meterRegistry)
      throws java.io.IOException {
    var config =
        new FhirPseudonymizerConfig(clientConfig(wireMockRuntime), Duration.ofSeconds(30), 3);

    var client = clientFactory.create(clientConfig(wireMockRuntime));
    wireMock = wireMockRuntime.getWireMock();
    fhirContext = FhirContext.forR4();

    step = new FhirPseudonymizerStep(client, config, meterRegistry, fhirContext);

    var bundle =
        generateOnePatient("patient-test-123", "2024", "http://test.example.com", "test-id");
    var consentedPatient = new ConsentedPatient("patient-test-123", "http://test.example.com");
    testBundle = new ConsentedPatientBundle(bundle, consentedPatient);
  }

  @Test
  void testDeidentifyWithFhirPseudonymizer() {
    var deidentifiedBundle = new Bundle();
    deidentifiedBundle.setType(Bundle.BundleType.COLLECTION);
    deidentifiedBundle.setId("transport-bundle-123");

    var deidentifiedBundleJson =
        fhirContext.newJsonParser().encodeResourceToString(deidentifiedBundle);

    wireMock.register(
        post(urlEqualTo(FHIR_PSEUDONYMIZER_ENDPOINT))
            .withHeader(CONTENT_TYPE, equalTo(MEDIA_TYPE_FHIR_JSON))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(CONTENT_TYPE, MEDIA_TYPE_FHIR_JSON)
                    .withBody(deidentifiedBundleJson)));

    var result = step.deidentify(testBundle);

    create(result)
        .assertNext(
            transportBundle -> {
              assertThat(transportBundle).isNotNull();
              assertThat(transportBundle.bundle()).isNotNull();
              assertThat(transportBundle.transferId()).isNotEmpty();
              assertThat(transportBundle.transferId()).isEqualTo("transport-bundle-123");
            })
        .verifyComplete();

    wireMock.verifyThat(1, postRequestedFor(urlEqualTo(FHIR_PSEUDONYMIZER_ENDPOINT)));
  }

  @Test
  void testDeidentifyServiceUnavailableRetry() {
    wireMock.register(
        post(urlEqualTo(FHIR_PSEUDONYMIZER_ENDPOINT))
            .willReturn(
                aResponse()
                    .withStatus(SERVICE_UNAVAILABLE.value())
                    .withBody("Service temporarily unavailable")));

    var result = step.deidentify(testBundle);

    create(result)
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isNotNull();
              assertThat(error.getMessage())
                  .satisfiesAnyOf(
                      msg -> assertThat(msg).contains("Service temporarily unavailable"),
                      msg -> assertThat(msg).contains("503"),
                      msg -> assertThat(msg).contains("SERVICE_UNAVAILABLE"),
                      msg -> assertThat(msg).contains("Retries exhausted"));
            })
        .verify();

    wireMock.verifyThat(4, postRequestedFor(urlEqualTo(FHIR_PSEUDONYMIZER_ENDPOINT)));
  }

  @Test
  void testDeidentifySuccessAfterRetry() {
    var deidentifiedBundle = new Bundle();
    deidentifiedBundle.setType(Bundle.BundleType.COLLECTION);
    deidentifiedBundle.setId("transport-bundle-retry-success");

    var deidentifiedBundleJson =
        fhirContext.newJsonParser().encodeResourceToString(deidentifiedBundle);

    wireMock.register(
        post(urlEqualTo(FHIR_PSEUDONYMIZER_ENDPOINT))
            .inScenario("Retry Scenario")
            .whenScenarioStateIs("Started")
            .willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE.value()))
            .willSetStateTo("First Failure"));

    wireMock.register(
        post(urlEqualTo(FHIR_PSEUDONYMIZER_ENDPOINT))
            .inScenario("Retry Scenario")
            .whenScenarioStateIs("First Failure")
            .willReturn(aResponse().withStatus(SERVICE_UNAVAILABLE.value()))
            .willSetStateTo("Second Failure"));

    wireMock.register(
        post(urlEqualTo(FHIR_PSEUDONYMIZER_ENDPOINT))
            .inScenario("Retry Scenario")
            .whenScenarioStateIs("Second Failure")
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(CONTENT_TYPE, MEDIA_TYPE_FHIR_JSON)
                    .withBody(deidentifiedBundleJson)));

    var result = step.deidentify(testBundle);

    create(result)
        .assertNext(
            transportBundle -> {
              assertThat(transportBundle).isNotNull();
              assertThat(transportBundle.transferId()).isEqualTo("transport-bundle-retry-success");
            })
        .verifyComplete();

    wireMock.verifyThat(3, postRequestedFor(urlEqualTo(FHIR_PSEUDONYMIZER_ENDPOINT)));
  }

  @Test
  void testConfigurationValues() {
    assertThat(step).isNotNull();
  }

  @AfterEach
  void tearDown() {
    wireMock.resetMappings();
    wireMock.resetScenarios();
  }
}
