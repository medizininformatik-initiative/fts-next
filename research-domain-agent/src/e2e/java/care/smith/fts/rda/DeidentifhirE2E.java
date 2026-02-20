package care.smith.fts.rda;

import static ca.uhn.fhir.context.FhirContext.forR4;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.rda.TransferProcessRunner.Phase;
import care.smith.fts.rda.TransferProcessRunner.Status;
import care.smith.fts.test.FhirGenerators;
import care.smith.fts.util.fhir.FhirDecoder;
import care.smith.fts.util.fhir.FhirEncoder;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.StringType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;
import org.wiremock.integrations.testcontainers.WireMockContainer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * E2E test for the deprecated DeidentiFHIR-based deidentification pipeline in RDA. Validates that
 * the old DeidentiFHIR flow still works end-to-end before removal.
 */
@Slf4j
public class DeidentifhirE2E {
  private final FhirContext fhirContext = forR4();
  private final FhirEncoder fhirEncoder = new FhirEncoder(fhirContext);
  private final FhirDecoder fhirDecoder = new FhirDecoder(fhirContext);

  private Network network = Network.newNetwork();
  private String buildId = System.getenv("BUILD_ID");

  private GenericContainer<?> rda =
      new GenericContainer<>(
              "ghcr.io/medizininformatik-initiative/fts/research-domain-agent:"
                  + (buildId != null ? buildId : "local"))
          .withCopyFileToContainer(
              MountableFile.forClasspathResource("projects/deidentifhir-example.yaml"),
              "/app/projects/deidentifhir-example.yaml")
          .withCopyFileToContainer(
              MountableFile.forClasspathResource("application.yaml"), "/app/application.yaml")
          .withFileSystemBind(
              Paths.get("src/e2e/resources/deidentifhir").toAbsolutePath().toString(),
              "/app/projects/deidentifhir-example/deidentifhir",
              BindMode.READ_ONLY)
          .withNetwork(network)
          .withExposedPorts(8080)
          .withLogConsumer(outputFrame -> System.out.print("RDA: " + outputFrame.getUtf8String()))
          .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200));

  private WireMockContainer trustCenterAgent =
      new WireMockContainer("wiremock/wiremock:3.13.0")
          .withNetwork(network)
          .withNetworkAliases("trust-center-agent");

  private WireMockContainer fhirStore =
      new WireMockContainer("wiremock/wiremock:3.13.0")
          .withNetwork(network)
          .withNetworkAliases("fhir-store");

  @BeforeEach
  void setUp() throws IOException {
    trustCenterAgent.start();
    fhirStore.start();
    rda.start();

    configureTcaMocks();
    configureFhirStoreMocks();
  }

  private void configureTcaMocks() {
    var tcaWireMock = new WireMock(trustCenterAgent.getHost(), trustCenterAgent.getPort());

    tcaWireMock.register(
        post(urlPathMatching("/api/v2/rd/secure-mapping"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(CONTENT_TYPE, "application/json")
                    .withBody(
                        """
                        {
                          "tidPidMap": {
                            "patient-102931": "pseudonym-102931",
                            "patient-identifier-102931": "pseudonym-identifier-102931"
                          },
                          "dateShiftMap": {}
                        }
                        """)));
  }

  private void configureFhirStoreMocks() {
    var fhirStoreWireMock = new WireMock(fhirStore.getHost(), fhirStore.getPort());

    fhirStoreWireMock.register(
        post(urlPathMatching("/fhir"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(CONTENT_TYPE, "application/fhir+json")
                    .withBody(
                        """
                        {
                          "resourceType": "OperationOutcome",
                          "issue": []
                        }
                        """)));
  }

  private Bundle createTestBundle(String transferId) throws IOException {
    var bundle = new Bundle();
    bundle.setType(Bundle.BundleType.COLLECTION);

    var patient =
        FhirGenerators.patient(
                () -> "patient-102931",
                () -> "http://fts.smith.care",
                () -> "patient-identifier-102931",
                () -> "1990")
            .generateResource();
    bundle.addEntry(new BundleEntryComponent().setResource(patient));

    var parameters = new Parameters();
    parameters.setId("transfer-id");
    parameters.addParameter("id", new StringType(transferId));
    bundle.addEntry(new BundleEntryComponent().setResource(parameters));

    return bundle;
  }

  @AfterEach
  void tearDown() {
    resetWireMockMappings();
    stopContainers();
  }

  private void resetWireMockMappings() {
    if (trustCenterAgent.isRunning()) {
      var tcaWireMock = new WireMock(trustCenterAgent.getHost(), trustCenterAgent.getPort());
      tcaWireMock.resetMappings();
    }
    if (fhirStore.isRunning()) {
      var fhirStoreWireMock = new WireMock(fhirStore.getHost(), fhirStore.getPort());
      fhirStoreWireMock.resetMappings();
    }
  }

  private void stopContainers() {
    if (rda.isRunning()) {
      rda.stop();
    }
    if (fhirStore.isRunning()) {
      fhirStore.stop();
    }
    if (trustCenterAgent.isRunning()) {
      trustCenterAgent.stop();
    }
    network.close();
  }

  private Flux<Status> createStatusPoller(WebClient webClient, String contentLocation) {
    return Flux.interval(Duration.ofSeconds(1))
        .take(10)
        .flatMap(tick -> pollStatus(webClient, contentLocation, tick))
        .takeUntil(this::isTerminalPhase);
  }

  private Mono<Status> pollStatus(WebClient webClient, String contentLocation, long tick) {
    log.info("Polling attempt: {}", tick + 1);
    return webClient
        .get()
        .uri(contentLocation)
        .retrieve()
        .bodyToMono(Status.class)
        .doOnNext(status -> log.info("Current phase: {}", status.phase()))
        .doOnError(error -> log.error("Error polling status: {}", error.getMessage()))
        .onErrorReturn(new Status("", Phase.ERROR, 0, 0));
  }

  private boolean isTerminalPhase(Status status) {
    var phase = status.phase();
    return phase == Phase.COMPLETED || phase == Phase.ERROR;
  }

  @Test
  void testDeidentifhirPipeline() throws IOException {
    var rdaBaseUrl = "http://" + rda.getHost() + ":" + rda.getMappedPort(8080);
    var webClient = createWebClientWithFhirCodecs(rdaBaseUrl);
    var testBundle = createTestBundle("transfer-123");

    var response = startTransferProcess(webClient, testBundle);

    StepVerifier.create(response)
        .assertNext(responseEntity -> verifyTransferAndStatus(responseEntity, webClient))
        .verifyComplete();
  }

  private WebClient createWebClientWithFhirCodecs(String baseUrl) {
    return WebClient.builder()
        .baseUrl(baseUrl)
        .codecs(
            configurer -> {
              configurer.customCodecs().register(fhirEncoder);
              configurer.customCodecs().register(fhirDecoder);
            })
        .build();
  }

  private Mono<ResponseEntity<Void>> startTransferProcess(WebClient webClient, Bundle testBundle) {
    return webClient
        .post()
        .uri("/api/v2/process/deidentifhir-example/patient")
        .header("Content-Type", "application/fhir+json")
        .bodyValue(testBundle)
        .retrieve()
        .toBodilessEntity();
  }

  private void verifyTransferAndStatus(ResponseEntity<Void> responseEntity, WebClient webClient) {
    assertThat(responseEntity.getStatusCode().value()).isEqualTo(202);

    var contentLocation = responseEntity.getHeaders().getFirst("Content-Location");
    assertThat(contentLocation).isNotNull();
    assertThat(contentLocation).contains("/api/v2/process/status/");

    log.info("Transfer started successfully!");
    log.info("Status URL: {}", contentLocation);

    verifyProcessCompletion(webClient, contentLocation);
  }

  private void verifyProcessCompletion(WebClient webClient, String contentLocation) {
    var statusPolling = createStatusPoller(webClient, contentLocation);

    StepVerifier.create(statusPolling)
        .thenConsumeWhile(status -> !isTerminalPhase(status))
        .assertNext(this::verifyFinalStatus)
        .verifyComplete();
  }

  private void verifyFinalStatus(Status status) {
    log.info("Final status phase: {}", status.phase());
    assertThat(status.phase()).isEqualTo(Phase.COMPLETED);
  }
}
