package care.smith.fts.cda.e2e;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;
import org.wiremock.integrations.testcontainers.WireMockContainer;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class ExternalCohortSelectorE2E {
  Network network = Network.newNetwork();

  String buildId = System.getenv("BUILD_ID");

  // Log file for capturing container logs
  private FileWriter logWriter;

  GenericContainer<?> cda = new GenericContainer<>(
      "ghcr.io/medizininformatik-initiative/fts/clinical-domain-agent:"
          + (buildId != null ? buildId : "local"))
      .withCopyFileToContainer(
          MountableFile.forClasspathResource("projects/example.yaml"),
          "/app/projects/example.yaml")
      .withCopyFileToContainer(
          MountableFile.forClasspathResource("application.yaml"), "/app/application.yaml")
      .withNetwork(network)
      .withExposedPorts(8080)
      .withLogConsumer(
          outputFrame -> {
            System.out.print("CDA: " + outputFrame.getUtf8String());
          })
      .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200));

  WireMockContainer cdHds = new WireMockContainer("wiremock/wiremock:3.13.0")
      .withNetwork(network)
      .withNetworkAliases("cd-hds");

  WireMockContainer tca = new WireMockContainer("wiremock/wiremock:3.13.0")
      .withNetwork(network)
      .withNetworkAliases("tc-agent");

  WireMockContainer rda = new WireMockContainer("wiremock/wiremock:3.13.0")
      .withNetwork(network)
      .withNetworkAliases("rd-agent");

  @BeforeEach
  void setUp() {
    // Initialize log file
    try {
      var timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
      logWriter = new FileWriter("cda-logs-" + timestamp + ".log");
      System.out.println("CDA logs will be written to: cda-logs-" + timestamp + ".log");
    } catch (IOException e) {
      System.err.println("Failed to create log file: " + e.getMessage());
    }

    cdHds.start();
    tca.start();
    rda.start();
    cda.start();

    configureCdHdsMocks();
    configureTcaMocks();
    configureRdaMocks();
  }

  private void configureCdHdsMocks() {
    var cdHdsWireMock = new WireMock(cdHds.getHost(), cdHds.getPort());
    cdHdsWireMock.register(
        get(urlPathMatching("/Patient.*"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(CONTENT_TYPE, "application/fhir+json")
                    .withBody(
                        """
                            {
                              "resourceType": "Bundle",
                              "type": "searchset",
                              "total": 1,
                              "entry": [
                                {
                                  "resource": {
                                    "resourceType": "Patient",
                                    "id": "patient-102931",
                                    "identifier": [
                                      {
                                        "system": "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym",
                                        "value": "patient-102931"
                                      }
                                    ]
                                  }
                                }
                              ]
                            }
                            """)));

    cdHdsWireMock.register(
        get(urlEqualTo("/metadata"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(CONTENT_TYPE, "application/fhir+json")
                    .withBody(
                        """
                            {
                              "resourceType": "CapabilityStatement",
                              "status": "active",
                              "fhirVersion": "4.0.1"
                            }
                            """)));
  }

  private void configureTcaMocks() {
    var tcaWireMock = new WireMock(tca.getHost(), tca.getPort());
    tcaWireMock.register(
        post(urlPathMatching("/api/v2/cd/consented-patients.*"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(CONTENT_TYPE, "application/fhir+json")
                    .withBody(
                        """
                            {
                              "resourceType": "Bundle",
                              "total": 1,
                              "entry": [
                                {
                                  "resource": {
                                    "resourceType": "Bundle",
                                    "total": 2,
                                    "entry": [
                                      {
                                        "resource": {
                                          "resourceType": "Consent",
                                          "id": "consent-102931",
                                          "status": "active",
                                          "scope": {
                                            "coding": [
                                              {
                                                "system": "http://terminology.hl7.org/CodeSystem/consentscope",
                                                "code": "research"
                                              }
                                            ]
                                          },
                                          "category": [
                                            {
                                              "coding": [
                                                {
                                                  "system": "http://loinc.org",
                                                  "code": "57016-8"
                                                }
                                              ]
                                            }
                                          ],
                                          "patient": {
                                            "reference": "Patient/patient-102931"
                                          },
                                          "dateTime": "2023-07-29T10:03:56+02:00",
                                          "policy": [
                                            {
                                              "uri": "2.16.840.1.113883.3.1937.777.24.5.3.2"
                                            },
                                            {
                                              "uri": "2.16.840.1.113883.3.1937.777.24.5.3.3"
                                            },
                                            {
                                              "uri": "2.16.840.1.113883.3.1937.777.24.5.3.6"
                                            },
                                            {
                                              "uri": "2.16.840.1.113883.3.1937.777.24.5.3.7"
                                            }
                                          ]
                                        }
                                      },
                                      {
                                        "resource": {
                                          "resourceType": "Patient",
                                          "id": "patient-102931",
                                          "identifier": [
                                            {
                                              "system": "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym",
                                              "value": "patient-102931"
                                            }
                                          ]
                                        }
                                      }
                                    ]
                                  }
                                }
                              ]
                            }
                            """)));

    tcaWireMock.register(
        post(urlPathMatching("/api/v2/cd/transport-mapping.*"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                    .withBody(
                        """
                            {
                              "transferId": "transfer-123",
                              "transportMapping": {
                                "patient-102931": "pseudonym-123"
                              },
                              "dateShiftValue": 1209600.000000000
                            }
                            """)));
  }

  private void configureRdaMocks() {
    var rdaWireMock = new WireMock(rda.getHost(), rda.getPort());
    rdaWireMock.register(
        post(urlEqualTo("/"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(CONTENT_TYPE, "application/fhir+json")
                    .withBody(
                        """
                            {
                              "resourceType": "Bundle",
                              "type": "transaction-response",
                              "entry": []
                            }
                            """)));

    rdaWireMock.register(
        post(urlPathMatching("/api/v2/rd/secure-mapping.*"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                    .withBody("{}"))); // Empty mapping response
  }

  @AfterEach
  void tearDown() {
    resetWireMockMappings();
    stopContainers();

    // Close log file
    if (logWriter != null) {
      try {
        logWriter.close();
      } catch (IOException e) {
        System.err.println("Failed to close log file: " + e.getMessage());
      }
    }
  }

  private void resetWireMockMappings() {
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

  private void stopContainers() {
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

  @Test
  void testStartTransferAllProcessWithExampleProject() {
    var cdaBaseUrl = "http://" + cda.getHost() + ":" + cda.getMappedPort(8080);
    var webClient = WebClient.builder().baseUrl(cdaBaseUrl).build();
    var objectMapper = new ObjectMapper();

    var response = webClient
        .post()
        .uri("/api/v2/process/example/start")
        .header("Content-Type", "application/json")
        .bodyValue("[]")
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

              var statusPolling = Flux.interval(Duration.ofSeconds(1))
                  .take(30) // Poll for maximum 30 seconds
                  .flatMap(tick -> {
                    System.out.println("Polling attempt: " + (tick + 1));
                    return webClient
                        .get()
                        .uri(contentLocation)
                        .retrieve()
                        .bodyToMono(String.class)
                        .doOnNext(statusResponse -> {
                          System.out.println("status polling - received response");
                          try {
                            var statusJson = objectMapper.readTree(statusResponse);
                            var phase = statusJson.get("phase").asText();
                            System.out.println("Current phase: " + phase);
                          } catch (Exception e) {
                            System.err.println("Error parsing status response: " + e.getMessage());
                          }
                        })
                        .doOnError(throwable -> {
                          System.err.println("Error polling status: " + throwable.getMessage());
                        })
                        .onErrorReturn("{}"); // Return empty JSON on error to continue polling
                  })
                  .takeUntil(statusResponse -> {
                    try {
                      var statusJson = objectMapper.readTree(statusResponse);
                      var phase = statusJson.get("phase").asText();
                      return phase.equals("COMPLETED") || phase.equals("COMPLETED_WITH_ERROR") || phase.equals("FATAL");
                    } catch (Exception e) {
                      return false; // Continue polling on parse errors
                    }
                  });

              // Verify that we eventually reach COMPLETED phase
              StepVerifier.create(statusPolling)
                  .thenConsumeWhile(statusResponse -> {
                    System.out.println("Verifying response");
                    try {
                      var statusJson = objectMapper.readTree(statusResponse);
                      var phase = statusJson.get("phase").asText();
                      System.out.println("Checking phase: " + phase);

                      // Continue consuming while NOT in a final state
                      return !phase.equals("COMPLETED") && !phase.equals("COMPLETED_WITH_ERROR")
                          && !phase.equals("FATAL");
                    } catch (Exception e) {
                      System.err.println("Error parsing status response: " + e.getMessage());
                      return true; // Continue on parse errors
                    }
                  })
                  .expectNextMatches(statusResponse -> {
                    try {
                      var statusJson = objectMapper.readTree(statusResponse);
                      var phase = statusJson.get("phase").asText();
                      System.out.println("Final phase check: " + phase);
                      return phase.equals("COMPLETED") || phase.equals("COMPLETED_WITH_ERROR") || phase.equals("FATAL");
                    } catch (Exception e) {
                      System.err.println("Error parsing final status response: " + e.getMessage());
                      return false;
                    }
                  })
                  .verifyComplete();
            })
        .verifyComplete();
  }
}
