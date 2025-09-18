package care.smith.fts.cda.e2e;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_LOCATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;
import org.wiremock.integrations.testcontainers.WireMockContainer;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * Abstract base class for CDA E2E tests that test different cohort selectors.
 * Provides common infrastructure setup, container management, and status polling logic.
 */
public abstract class AbstractCohortSelectorE2E {
  protected Network network = Network.newNetwork();
  protected String buildId = System.getenv("BUILD_ID");

  protected GenericContainer<?> cda =
      new GenericContainer<>(
              "ghcr.io/medizininformatik-initiative/fts/clinical-domain-agent:"
                  + (buildId != null ? buildId : "local"))
          .withCopyFileToContainer(
              MountableFile.forClasspathResource("projects/" + getProjectFileName()),
              "/app/projects/example.yaml")
          .withCopyFileToContainer(
              MountableFile.forClasspathResource("application.yaml"), "/app/application.yaml")
          .withFileSystemBind(
              Paths.get("src/e2e/resources/deidentifhir").toAbsolutePath().toString(),
              "/app/projects/example/deidentifhir",
              BindMode.READ_ONLY)
          .withNetwork(network)
          .withExposedPorts(8080)
          .withLogConsumer(outputFrame -> System.out.print("CDA: " + outputFrame.getUtf8String()))
          .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200));

  protected WireMockContainer cdHds =
      new WireMockContainer("wiremock/wiremock:3.13.0")
          .withNetwork(network)
          .withNetworkAliases("cd-hds");

  protected WireMockContainer tca =
      new WireMockContainer("wiremock/wiremock:3.13.0")
          .withNetwork(network)
          .withNetworkAliases("tc-agent");

  protected WireMockContainer rda =
      new WireMockContainer("wiremock/wiremock:3.13.0")
          .withNetwork(network)
          .withNetworkAliases("rd-agent");

  /**
   * Returns the project YAML filename for this specific cohort selector test.
   *
   * @return filename relative to the projects directory (e.g., "external-consent-example.yaml")
   */
  protected abstract String getProjectFileName();

  /**
   * Configure CD-HDS specific mocks for this cohort selector type.
   *
   * @throws IOException if mock configuration fails
   */
  protected abstract void configureCdHdsMocks() throws IOException;

  /**
   * Configure TCA specific mocks for this cohort selector type.
   */
  protected abstract void configureTcaMocks();

  /**
   * Returns the list of test body values to test with.
   * Each value will be used in a separate test method.
   *
   * @return list of JSON string body values
   */
  protected abstract List<String> getTestBodyValues();

  /**
   * Validates the final status response. Default implementation checks totalBundles and sentBundles.
   * Override if specific cohort selector needs different validation.
   *
   * @param statusJson the parsed status JSON response
   * @return true if validation passes, false otherwise
   */
  protected boolean validateFinalStatus(JsonNode statusJson) {
    try {
      var totalBundles = statusJson.get("totalBundles").asLong();
      assertThat(totalBundles).isEqualTo(1);
      var sentBundles = statusJson.get("sentBundles").asLong();
      assertThat(sentBundles).isEqualTo(1);
      return true;
    } catch (Exception e) {
      System.err.println("Error validating final status: " + e.getMessage());
      return false;
    }
  }

  @BeforeEach
  void setUp() throws IOException {
    cdHds.start();
    tca.start();
    rda.start();
    cda.start();

    configureCdHdsMocks();
    configureTcaMocks();
    configureRdaMocks();
  }

  /**
   * Configure RDA mocks. Default implementation should work for most cases.
   * Override if specific RDA mock behavior is needed.
   */
  protected void configureRdaMocks() {
    var rdaWireMock = new WireMock(rda.getHost(), rda.getPort());
    rdaWireMock.register(
        post(urlEqualTo("/api/v2/process/example/patient"))
            .willReturn(
                aResponse()
                    .withStatus(202)
                    .withHeader(CONTENT_TYPE, "application/fhir+json")
                    .withHeader(
                        CONTENT_LOCATION,
                        "http://rd-agent:8080/api/v2/process/status/transfer-123")));

    rdaWireMock.register(
        get(urlEqualTo("/api/v2/process/status/transfer-123"))
            .willReturn(aResponse().withStatus(200)));
  }

  @AfterEach
  void tearDown() {
    resetWireMockMappings();
    stopContainers();
  }

  protected void resetWireMockMappings() {
    if (cdHds.isRunning()) {
      var cdHdsWireMock = new WireMock(cdHds.getHost(), cdHds.getPort());
      cdHdsWireMock.resetMappings();
    }
    if (tca.isRunning()) {
      var tcaWireMock = new WireMock(tca.getHost(), tca.getPort());
      tcaWireMock.resetMappings();
    }
    if (rda.isRunning()) {
      var rdaWireMock = new WireMock(rda.getHost(), rda.getPort());
      rdaWireMock.resetMappings();
    }
  }

  protected void stopContainers() {
    // Stop containers in reverse order
    if (cda != null && cda.isRunning()) {
      cda.stop();
    }
    if (rda != null && rda.isRunning()) {
      rda.stop();
    }
    if (tca != null && tca.isRunning()) {
      tca.stop();
    }
    if (cdHds != null && cdHds.isRunning()) {
      cdHds.stop();
    }
    if (network != null) {
      network.close();
    }
  }

  /**
   * Creates status polling logic that polls the given content location URL
   * until a final state is reached.
   *
   * @param webClient the WebClient to use for polling
   * @param objectMapper the ObjectMapper to parse responses
   * @param contentLocation the status URL to poll
   * @return a Flux that emits status responses until completion
   */
  protected Flux<String> createStatusPoller(WebClient webClient, ObjectMapper objectMapper, String contentLocation) {
    return Flux.interval(Duration.ofSeconds(1))
        .take(10) // Poll for maximum 10 seconds
        .flatMap(
            tick -> {
              System.out.println("Polling attempt: " + (tick + 1));
              return webClient
                  .get()
                  .uri(contentLocation)
                  .retrieve()
                  .bodyToMono(String.class)
                  .doOnNext(
                      statusResponse -> {
                        System.out.println("status polling - received response");
                        try {
                          var statusJson = objectMapper.readTree(statusResponse);
                          var phase = statusJson.get("phase").asText();
                          System.out.println("Current phase: " + phase);
                        } catch (Exception e) {
                          System.err.println(
                              "Error parsing status response: " + e.getMessage());
                        }
                      })
                  .doOnError(
                      throwable -> {
                        System.err.println(
                            "Error polling status: " + throwable.getMessage());
                      })
                  .onErrorReturn(
                      "{}"); // Return empty JSON on error to continue polling
            })
        .takeUntil(
            statusResponse -> {
              try {
                var statusJson = objectMapper.readTree(statusResponse);
                var phase = statusJson.get("phase").asText();
                return phase.equals("COMPLETED")
                    || phase.equals("COMPLETED_WITH_ERROR")
                    || phase.equals("FATAL");
              } catch (Exception e) {
                return false; // Continue polling on parse errors
              }
            });
  }

  /**
   * Executes a transfer test with the given body value.
   *
   * @param bodyValue the JSON body value to send in the request
   */
  protected void executeTransferTest(String bodyValue) {
    var cdaBaseUrl = "http://" + cda.getHost() + ":" + cda.getMappedPort(8080);
    var webClient = WebClient.builder().baseUrl(cdaBaseUrl).build();
    var objectMapper = new ObjectMapper();

    var response =
        webClient
            .post()
            .uri("/api/v2/process/example/start")
            .header("Content-Type", "application/json")
            .bodyValue(bodyValue)
            .retrieve()
            .toEntity(String.class);

    StepVerifier.create(response)
        .assertNext(
            responseEntity -> {
              assertThat(responseEntity.getStatusCode().value()).isEqualTo(202);

              var contentLocation = responseEntity.getHeaders().getFirst("Content-Location");
              assertThat(contentLocation).isNotNull();
              assertThat(contentLocation).contains("/api/v2/process/status/");

              System.out.println("Transfer started successfully!");
              System.out.println("Status URL: " + contentLocation);

              var statusPolling = createStatusPoller(webClient, objectMapper, contentLocation);

              // Verify that we eventually reach COMPLETED phase
              StepVerifier.create(statusPolling)
                  .thenConsumeWhile(
                      statusResponse -> {
                        System.out.println("Verifying response");
                        try {
                          var statusJson = objectMapper.readTree(statusResponse);
                          var phase = statusJson.get("phase").asText();
                          System.out.println("Checking phase: " + phase);
                          System.out.println("Status: " + statusResponse);

                          // Continue consuming while NOT in a final state
                          return !phase.equals("COMPLETED")
                              && !phase.equals("COMPLETED_WITH_ERROR")
                              && !phase.equals("FATAL");
                        } catch (Exception e) {
                          System.err.println("Error parsing status response: " + e.getMessage());
                          return true; // Continue on parse errors
                        }
                      })
                  .expectNextMatches(
                      statusResponse -> {
                        try {
                          var statusJson = objectMapper.readTree(statusResponse);
                          System.out.println("Status: " + statusResponse);

                          var phase = statusJson.get("phase").asText();
                          System.out.println("Final phase check: " + phase);

                          // First check that we reached COMPLETED phase
                          assertThat(phase).isEqualTo("COMPLETED");

                          // Then let the subclass validate specific details
                          return validateFinalStatus(statusJson);
                        } catch (Exception e) {
                          System.err.println(
                              "Error parsing final status response: " + e.getMessage());
                          return false;
                        }
                      })
                  .verifyComplete();
            })
        .verifyComplete();
  }

  /**
   * Default test that runs transfer tests for all body values returned by getTestBodyValues().
   * Subclasses can override to provide custom test methods.
   */
  @Test
  void testStartTransferAllProcessWithExampleProject() {
    var testBodyValues = getTestBodyValues();
    if (testBodyValues.size() == 1) {
      executeTransferTest(testBodyValues.get(0));
    } else {
      // If multiple body values, test them all
      for (String bodyValue : testBodyValues) {
        System.out.println("Testing with body value: " + bodyValue);
        executeTransferTest(bodyValue);
      }
    }
  }
}
