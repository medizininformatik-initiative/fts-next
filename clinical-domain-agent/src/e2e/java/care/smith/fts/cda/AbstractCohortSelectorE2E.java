package care.smith.fts.cda;

import static care.smith.fts.cda.TransferProcessRunner.Phase.COMPLETED;
import static care.smith.fts.cda.TransferProcessRunner.Phase.COMPLETED_WITH_ERROR;
import static care.smith.fts.cda.TransferProcessRunner.Phase.FATAL;
import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static care.smith.fts.test.MockServerUtil.jsonResponse;
import static care.smith.fts.util.fhir.FhirUtils.toBundle;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_LOCATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import care.smith.fts.test.FhirCohortGenerator;
import care.smith.fts.test.FhirGenerators;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
 * Abstract base class for CDA E2E tests that test different cohort selectors. Provides common
 * infrastructure setup, container management, and status polling logic.
 */
@Slf4j
public abstract class AbstractCohortSelectorE2E {
  protected final Network network = Network.newNetwork();
  protected final String buildId = System.getenv("BUILD_ID");
  private final String projectFileName;
  protected final GenericContainer<?> cda;

  protected AbstractCohortSelectorE2E(String projectFileName) {
    this.projectFileName = projectFileName;
    this.cda = createCdaContainer(projectFileName);
  }

  private GenericContainer<?> createCdaContainer(String projectFileName) {
    return new GenericContainer<>(
            "ghcr.io/medizininformatik-initiative/fts/clinical-domain-agent:"
                + (buildId != null ? buildId : "local"))
        .withCreateContainerCmdModifier(cmd -> cmd.withName(createContainerName("cda")))
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("projects/" + projectFileName),
            "/app/projects/example.yaml")
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("application.yaml"), "/app/application.yaml")
        .withFileSystemBind(
            Paths.get("src/e2e/resources/deidentifhir").toAbsolutePath().toString(),
            "/app/projects/example/deidentifhir",
            BindMode.READ_ONLY)
        .withNetwork(network)
        .withExposedPorts(8080)
        .withLogConsumer(outputFrame -> log.info(outputFrame.getUtf8String()))
        .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200));
  }

  private String getProjectName() {
    return projectFileName.replaceFirst("\\.[^.]*$", ""); // Remove extension
  }

  private String createContainerName(String service) {
    return "cda-e2e-" + service + "-" + getProjectName();
  }

  protected WireMockContainer cdHds =
      new WireMockContainer("wiremock/wiremock:3.13.0")
          .withCreateContainerCmdModifier(cmd -> cmd.withName(createContainerName("cd-hds")))
          .withNetwork(network)
          .withNetworkAliases("cd-hds");

  protected WireMockContainer tca =
      new WireMockContainer("wiremock/wiremock:3.13.0")
          .withCreateContainerCmdModifier(cmd -> cmd.withName(createContainerName("tc-agent")))
          .withNetwork(network)
          .withNetworkAliases("tc-agent");

  protected WireMockContainer rda =
      new WireMockContainer("wiremock/wiremock:3.13.0")
          .withCreateContainerCmdModifier(cmd -> cmd.withName(createContainerName("rd-agent")))
          .withNetwork(network)
          .withNetworkAliases("rd-agent");

  /**
   * Configure CD-HDS specific mocks for this cohort selector type.
   *
   * @throws IOException if mock configuration fails
   */
  protected void configureCdHdsMocks() throws IOException {
    setupCommonCdHdsMocks();
    setupSpecificCdHdsMocks();
  }

  /**
   * Setup common CD-HDS mocks that are shared across all cohort selector implementations. This
   * includes patient identifier resolution and basic patient data generation.
   */
  private void setupCommonCdHdsMocks() throws IOException {
    var cdHdsWireMock = new WireMock(cdHds.getHost(), cdHds.getPort());

    var resolveResponse =
        FhirGenerators.resolveSearchResponse(
                () -> "patient-1", () -> "patient-identifier-1", () -> "resolveId")
            .generateResource();

    cdHdsWireMock.register(
        get(urlPathMatching("/fhir/Patient"))
            .withQueryParam("identifier", equalTo("http://fts.smith.care|patient-identifier-1"))
            .willReturn(fhirResponse(resolveResponse)));

    var cohortGenerator = createCohortGenerator();
    var patient = cohortGenerator.generate();
    var patientBundle = Stream.of(patient).collect(toBundle());

    // Default patient GET mock - can be overridden by specific implementations
    cdHdsWireMock.register(
        get(urlPathMatching("/fhir/Patient/patient-1.*")).willReturn(fhirResponse(patientBundle)));
  }

  /** Creates the standard FhirCohortGenerator used across all implementations. */
  protected FhirCohortGenerator createCohortGenerator() {
    return createCohortGenerator("http://fts.smith.care");
  }

  /**
   * Creates a FhirCohortGenerator with the specified pidSystem.
   *
   * @param pidSystem the patient identifier system to use
   * @return configured FhirCohortGenerator
   */
  protected FhirCohortGenerator createCohortGenerator(String pidSystem) {
    return new FhirCohortGenerator(
        pidSystem,
        "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3",
        Set.of(
            "2.16.840.1.113883.3.1937.777.24.5.3.3",
            "2.16.840.1.113883.3.1937.777.24.5.3.2",
            "2.16.840.1.113883.3.1937.777.24.5.3.7",
            "2.16.840.1.113883.3.1937.777.24.5.3.6"));
  }

  /**
   * Setup cohort-selector-specific CD-HDS mocks. Default implementation does nothing. Override this
   * method to add additional mocks specific to your cohort selector type.
   */
  protected void setupSpecificCdHdsMocks() throws IOException {
    // Default: no additional mocks needed
  }

  /** Configure TCA specific mocks for this cohort selector type. */
  protected void configureTcaMocks() {
    setupCommonTcaMocks();
    setupSpecificTcaMocks();
  }

  /**
   * Setup common TCA mocks that are shared across all cohort selector implementations. This
   * includes the standard transport-mapping response.
   */
  private void setupCommonTcaMocks() {
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
                      "dateShiftMapping": {}
                    }
                    """)));
  }

  /**
   * Setup cohort-selector-specific TCA mocks. Default implementation does nothing. Override this
   * method to add additional mocks specific to your cohort selector type.
   */
  protected void setupSpecificTcaMocks() {
    // Default: no additional mocks needed
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
   * Configure RDA mocks. Default implementation should work for most cases. Override if specific
   * RDA mock behavior is needed.
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
    if (cda.isRunning()) {
      cda.stop();
    }
    if (rda.isRunning()) {
      rda.stop();
    }
    if (tca.isRunning()) {
      tca.stop();
    }
    if (cdHds.isRunning()) {
      cdHds.stop();
    }
    network.close();
  }

  /**
   * Creates status polling logic that polls the given content location URL until a final state is
   * reached.
   *
   * @param webClient the WebClient to use for polling
   * @param contentLocation the status URL to poll
   * @return a Flux that emits status responses until completion
   */
  protected Flux<TransferProcessStatus> createStatusPoller(
      WebClient webClient, String contentLocation) {
    return Flux.interval(Duration.ofSeconds(1))
        .take(10) // Poll for maximum 10 seconds
        .flatMap(tick -> performSingleStatusPoll(webClient, contentLocation, tick))
        .takeUntil(this::isTerminalPhase);
  }

  private reactor.core.publisher.Mono<TransferProcessStatus> performSingleStatusPoll(
      WebClient webClient, String contentLocation, long tick) {
    log.info("Polling attempt: {}", tick + 1);
    return webClient
        .get()
        .uri(contentLocation)
        .retrieve()
        .bodyToMono(TransferProcessStatus.class)
        .doOnNext(status -> log.info("Current phase: {}", status.phase()))
        .doOnError(throwable -> log.error("Error polling status: {}", throwable.getMessage()))
        .onErrorReturn(
            TransferProcessStatus.create("")
                .setPhase(COMPLETED_WITH_ERROR)); // Return empty JSON on error to continue polling
  }

  private boolean isTerminalPhase(TransferProcessStatus status) {
    var phase = status.phase();
    return phase.equals(COMPLETED) || phase.equals(COMPLETED_WITH_ERROR) || phase.equals(FATAL);
  }

  /**
   * Executes a transfer test with the given body value.
   *
   * @param bodyValue the JSON body value to send in the request
   */
  protected void executeTransferTest(String bodyValue) {
    var webClient = setupWebClient();
    var response = sendTransferRequest(webClient, bodyValue);
    verifyTransferResponse(response, webClient);
  }

  /** Sets up WebClient and ObjectMapper for the test. */
  private WebClient setupWebClient() {
    var cdaBaseUrl = "http://" + cda.getHost() + ":" + cda.getMappedPort(8080);
    return WebClient.builder().baseUrl(cdaBaseUrl).build();
  }

  /** Sends the transfer request and returns the response. */
  private Mono<ResponseEntity<String>> sendTransferRequest(WebClient webClient, String bodyValue) {
    return webClient
        .post()
        .uri("/api/v2/process/example/start")
        .header("Content-Type", "application/json")
        .bodyValue(bodyValue)
        .retrieve()
        .toEntity(String.class);
  }

  /** Verifies the transfer response and handles status polling. */
  private void verifyTransferResponse(Mono<ResponseEntity<String>> response, WebClient webClient) {
    StepVerifier.create(response)
        .assertNext(
            responseEntity -> {
              validateInitialResponse(responseEntity);
              var contentLocation = extractContentLocation(responseEntity);
              verifyStatusPolling(webClient, contentLocation);
            })
        .verifyComplete();
  }

  /** Validates the initial transfer response. */
  private void validateInitialResponse(ResponseEntity<String> responseEntity) {
    assertThat(responseEntity.getStatusCode().value()).isEqualTo(202);
  }

  /** Extracts and validates the Content-Location header. */
  private String extractContentLocation(ResponseEntity<String> responseEntity) {
    var contentLocation = responseEntity.getHeaders().getFirst("Content-Location");
    assertThat(contentLocation).isNotNull();
    assertThat(contentLocation).contains("/api/v2/process/status/");

    log.info("Transfer started successfully!");
    log.info("Status URL: {}", contentLocation);

    return contentLocation;
  }

  /** Verifies the status polling until completion. */
  private void verifyStatusPolling(WebClient webClient, String contentLocation) {
    var statusPolling = createStatusPoller(webClient, contentLocation);

    StepVerifier.create(statusPolling)
        .thenConsumeWhile(this::isNotTerminalPhase)
        .assertNext(this::validateCompletedPhase)
        .verifyComplete();
  }

  /** Checks if the current phase is not a final state. */
  private boolean isNotTerminalPhase(TransferProcessStatus status) {
    return !isTerminalPhase(status);
  }

  /** Validates the final completed phase and status. */
  private void validateCompletedPhase(TransferProcessStatus status) {
    assertThat(status.phase()).isEqualTo(COMPLETED);
    assertThat(status.totalBundles()).isEqualTo(1);
    assertThat(status.sentBundles()).isEqualTo(1);
  }
}
