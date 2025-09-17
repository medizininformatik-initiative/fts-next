package care.smith.fts.cda.e2e;

import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static care.smith.fts.test.MockServerUtil.jsonResponse;
import static care.smith.fts.util.fhir.FhirUtils.fhirResourceToString;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_LOCATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import care.smith.fts.test.FhirCohortGenerator;
import care.smith.fts.test.FhirGenerators;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Set;
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

public class FHIRCohortSelectorE2E {
  Network network = Network.newNetwork();

  String buildId = System.getenv("BUILD_ID");

  GenericContainer<?> cda =
      new GenericContainer<>(
              "ghcr.io/medizininformatik-initiative/fts/clinical-domain-agent:"
                  + (buildId != null ? buildId : "local"))
          .withCopyFileToContainer(
              MountableFile.forClasspathResource("projects/fhir-consent-example.yaml"),
              "/app/projects/example.yaml")
          .withCopyFileToContainer(
              MountableFile.forClasspathResource("application.yaml"), "/app/application.yaml")
          .withFileSystemBind(
              Paths.get("src/e2e/resources/deidentifhir").toAbsolutePath().toString(), // host path
              "/app/projects/example/deidentifhir", // container path
              BindMode.READ_ONLY)
          .withNetwork(network)
          .withExposedPorts(8080)
          .withLogConsumer(
              outputFrame -> {
                System.out.print("CDA: " + outputFrame.getUtf8String());
              })
          .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200));

  WireMockContainer cdHds =
      new WireMockContainer("wiremock/wiremock:3.13.0")
          .withNetwork(network)
          .withNetworkAliases("cd-hds")
          .withLogConsumer(
              outputFrame -> {
                System.out.print("CD-HDS WireMock: " + outputFrame.getUtf8String());
              });

  WireMockContainer tca =
      new WireMockContainer("wiremock/wiremock:3.13.0")
          .withNetwork(network)
          .withNetworkAliases("tc-agent");

  WireMockContainer rda =
      new WireMockContainer("wiremock/wiremock:3.13.0")
          .withNetwork(network)
          .withNetworkAliases("rd-agent");

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

  private void configureCdHdsMocks() throws IOException {
    var cdHdsWireMock = new WireMock(cdHds.getHost(), cdHds.getPort());

    // Enable request logging to see what URLs are being called
    System.out.println("=== WireMock CD-HDS Configuration ===");
    System.out.println("WireMock URL: http://" + cdHds.getHost() + ":" + cdHds.getPort());

    // Add a catch-all stub to log all requests that don't match other stubs
    cdHdsWireMock.register(
        any(anyUrl())
            .willReturn(
                aResponse().withStatus(404).withBody("{\"error\": \"No matching stub found\"}"))
            .atPriority(10) // Lower priority so specific stubs match first
        );

    var resolveResponse =
        FhirGenerators.resolveSearchResponse(
                () -> "patient-1", () -> "patient-identifier-1", () -> "resolveId")
            .generateResource();

    cdHdsWireMock.register(
        get(urlPathMatching("/fhir/Patient"))
            .withQueryParam("identifier", equalTo("http://fts.smith.care|patient-identifier-1"))
            .willReturn(fhirResponse(resolveResponse)));

    var cohortGenerator =
        new FhirCohortGenerator(
            "http://fts.smith.care",
            "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3",
            Set.of(
                "2.16.840.1.113883.3.1937.777.24.5.3.3",
                "2.16.840.1.113883.3.1937.777.24.5.3.2",
                "2.16.840.1.113883.3.1937.777.24.5.3.7",
                "2.16.840.1.113883.3.1937.777.24.5.3.6"));

    var patient = cohortGenerator.generate();
    System.out.println("Patient bundle: " + fhirResourceToString(patient));

    cdHdsWireMock.register(
        get(urlPathMatching("/fhir/Consent"))
            //            .withQueryParam("_include", equalTo("Consent:patient"))
            .willReturn(fhirResponse(patient)));

    //    cdHdsWireMock.register(
    //        get(urlPathMatching("/fhir/Consent"))
    //            .withQueryParam("_include", equalTo("Consent:patient"))
    //            .withQueryParam(
    //                "patient.identifier", equalTo("http://fts.smith.care|patient-identifier-1"))
    //            .willReturn(fhirResponse(patient)));

    cdHdsWireMock.register(
        get(urlPathMatching("/fhir/Patient/patient-1]")).willReturn(fhirResponse(patient)));
  }

  private void configureTcaMocks() {
    var tcaWireMock = new WireMock(tca.getHost(), tca.getPort());

    tcaWireMock.register(
        post(urlPathMatching("/api/v2/cd/transport-mapping.*"))
            .willReturn(
                jsonResponse(
                    """
                    {
                      "transferId": "transfer-123",
                      "transportMapping": {
                        "patient-identifier-1.Patient:patient-1": "pseudonym-123",
                        "patient-identifier-1.identifier.http://fts.smith.care:patient-identifier-1": "pseudonym-identifier-123"
                      },
                      "dateShiftValue": 1209600.000000000
                    }
                    """)));
  }

  private void configureRdaMocks() {
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
  void testStartTransferAllProcessWithTcaExampleProject() {
    testStartTransferWithBodyValue("[]");
  }

  //  @Test
  //  void testStartTransferProcessWithTcaExampleProject() {
  //    testStartTransferWithBodyValue("[\"patient-identifier-1\"]");
  //  }

  private void testStartTransferWithBodyValue(String bodyValue) {
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

              var statusPolling =
                  Flux.interval(Duration.ofSeconds(1))
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
                          assertThat(phase).isEqualTo("COMPLETED");
                          var totalBundles = statusJson.get("totalBundles").asLong();
                          assertThat(totalBundles).isEqualTo(1);
                          var sentBundles = statusJson.get("sentBundles").asLong();
                          assertThat(sentBundles).isEqualTo(1);

                          return true;
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
}
