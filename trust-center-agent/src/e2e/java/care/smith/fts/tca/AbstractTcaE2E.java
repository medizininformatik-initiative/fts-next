package care.smith.fts.tca;

import static care.smith.fts.test.FhirGenerators.gpasGetOrCreateResponse;
import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.util.fhir.FhirDecoder;
import care.smith.fts.util.fhir.FhirEncoder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.redis.testcontainers.RedisContainer;
import java.io.IOException;
import java.net.URI;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import org.wiremock.integrations.testcontainers.WireMockContainer;
import reactor.test.StepVerifier;

/**
 * Abstract base class for TCA E2E tests. Provides common infrastructure setup including TCA
 * container, WireMock mocks for gICS and gPAS, and Redis container for mapping storage.
 */
@Slf4j
public abstract class AbstractTcaE2E {
  protected final Network network = Network.newNetwork();
  protected final String buildId = System.getenv("BUILD_ID");

  protected final RedisContainer redis =
      new RedisContainer(DockerImageName.parse("valkey/valkey:8.1.3-alpine"))
          .withCreateContainerCmdModifier(cmd -> cmd.withName(createContainerName("redis")))
          .withNetwork(network)
          .withNetworkAliases("keystore");

  protected final WireMockContainer gics =
      new WireMockContainer("wiremock/wiremock:3.13.0")
          .withCreateContainerCmdModifier(cmd -> cmd.withName(createContainerName("gics")))
          .withNetwork(network)
          .withNetworkAliases("gics");

  protected final WireMockContainer gpas =
      new WireMockContainer("wiremock/wiremock:3.13.0")
          .withCreateContainerCmdModifier(cmd -> cmd.withName(createContainerName("gpas")))
          .withNetwork(network)
          .withNetworkAliases("gpas");

  protected final GenericContainer<?> tca;

  protected AbstractTcaE2E() {
    this.tca = createTcaContainer();
  }

  private GenericContainer<?> createTcaContainer() {
    return new GenericContainer<>(
            "ghcr.io/medizininformatik-initiative/fts/trust-center-agent:"
                + (buildId != null ? buildId : "local"))
        .withCreateContainerCmdModifier(cmd -> cmd.withName(createContainerName("tca")))
        .withCopyFileToContainer(
            MountableFile.forClasspathResource("application.yaml"), "/app/application.yaml")
        .withNetwork(network)
        .withExposedPorts(8080)
        .withEnv("CONSENT_GICS_FHIR_BASEURL", "http://gics:8080/ttp-fhir/fhir/gics")
        .withEnv("CONSENT_GICS_FHIR_AUTH_NONE", "")
        .withEnv("DEIDENTIFICATION_KEYSTOREURL", "redis://keystore:6379")
        .withEnv("DEIDENTIFICATION_GPAS_FHIR_BASEURL", "http://gpas:8080/ttp-fhir/fhir/gpas")
        .withEnv("DEIDENTIFICATION_GPAS_FHIR_AUTH_NONE", "")
        .withEnv("SECURITY_ENDPOINTS", "")
        .withLogConsumer(outputFrame -> log.info(outputFrame.getUtf8String()))
        .waitingFor(Wait.forHttp("/actuator/health").forStatusCode(200));
  }

  private String createContainerName(String service) {
    return "tca-e2e-" + service + "-" + getTestName();
  }

  /**
   * Returns the test name for container naming purposes. Override this method to provide a custom
   * test name.
   */
  protected String getTestName() {
    return this.getClass().getSimpleName();
  }

  /**
   * Configure gICS specific mocks for this test. Override this method to add specific mock
   * configurations.
   */
  protected void configureGicsMocks() throws IOException {
    // Default: no mocks configured
  }

  /**
   * Configure gPAS specific mocks for this test. Override this method to add specific mock
   * configurations.
   */
  protected void configureGpasMocks() throws IOException {
    // Default: no mocks configured
  }

  @BeforeEach
  void setUp() throws IOException {
    redis.start();
    gics.start();
    gpas.start();

    // Configure mocks BEFORE starting TCA so metadata endpoints are available
    configureGicsMocks();
    configureGpasMocks();

    tca.start();
  }

  @AfterEach
  void tearDown() {
    resetWireMockMappings();
    stopContainers();
  }

  protected void resetWireMockMappings() {
    if (gics.isRunning()) {
      var gicsWireMock = new WireMock(gics.getHost(), gics.getPort());
      gicsWireMock.resetMappings();
    }
    if (gpas.isRunning()) {
      var gpasWireMock = new WireMock(gpas.getHost(), gpas.getPort());
      gpasWireMock.resetMappings();
    }
  }

  protected void stopContainers() {
    if (tca.isRunning()) {
      tca.stop();
    }
    if (gpas.isRunning()) {
      gpas.stop();
    }
    if (gics.isRunning()) {
      gics.stop();
    }
    if (redis.isRunning()) {
      redis.stop();
    }
    network.close();
  }

  /** Creates a WebClient for TCA API calls with FHIR codec support. */
  protected WebClient createTcaWebClient() {
    var tcaBaseUrl = "http://" + tca.getHost() + ":" + tca.getMappedPort(8080);
    var fhirContext = FhirContext.forR4();
    var fhirDecoder = new FhirDecoder(fhirContext);
    var fhirEncoder = new FhirEncoder(fhirContext);

    return WebClient.builder()
        .baseUrl(tcaBaseUrl)
        .codecs(
            configurer -> {
              configurer.customCodecs().register(fhirDecoder);
              configurer.customCodecs().register(fhirEncoder);
            })
        .build();
  }

  // ===== Common Test Helpers =====

  protected static final String GICS_CAPABILITY_STATEMENT =
      """
      {
        "resourceType": "CapabilityStatement",
        "status": "active",
        "date": "2024-01-01",
        "kind": "instance",
        "fhirVersion": "4.0.1",
        "format": ["application/fhir+json"],
        "rest": [{
          "mode": "server",
          "operation": [{
            "name": "allConsentsForDomain"
          }, {
            "name": "allConsentsForPerson"
          }]
        }]
      }
      """;

  protected static final String GPAS_CAPABILITY_STATEMENT =
      """
      {
        "resourceType": "CapabilityStatement",
        "status": "active",
        "date": "2024-01-01",
        "kind": "instance",
        "fhirVersion": "4.0.1",
        "format": ["application/fhir+json"],
        "rest": [{
          "mode": "server",
          "operation": [{
            "name": "pseudonymizeAllowCreate"
          }]
        }]
      }
      """;

  /** Configures gICS metadata endpoint mock. */
  protected void configureGicsMetadataMock() throws IOException {
    var gicsWireMock = new WireMock(gics.getHost(), gics.getPort());
    gicsWireMock.register(
        get(urlPathEqualTo("/ttp-fhir/fhir/gics/metadata"))
            .willReturn(fhirResponse(GICS_CAPABILITY_STATEMENT)));
  }

  /** Configures gPAS metadata endpoint mock. */
  protected void configureGpasMetadataMock() throws IOException {
    var gpasWireMock = new WireMock(gpas.getHost(), gpas.getPort());
    gpasWireMock.register(
        get(urlPathEqualTo("/ttp-fhir/fhir/gpas/metadata"))
            .willReturn(fhirResponse(GPAS_CAPABILITY_STATEMENT)));
  }

  /**
   * Configures a gPAS pseudonymization mock for a specific original value.
   *
   * @param original The original identifier value
   * @param pseudonym The pseudonym to return
   */
  protected void configureGpasPseudonymizationMock(String original, String pseudonym)
      throws IOException {
    var gpasWireMock = new WireMock(gpas.getHost(), gpas.getPort());
    var generator = gpasGetOrCreateResponse(() -> original, () -> pseudonym);

    gpasWireMock.register(
        post(urlPathEqualTo("/ttp-fhir/fhir/gpas/$pseudonymizeAllowCreate"))
            .withRequestBody(
                equalToJson(
                    """
                    {
                      "resourceType": "Parameters",
                      "parameter": [
                        {"name": "target", "valueString": "domain"},
                        {"name": "original", "valueString": "%s"}
                      ]
                    }
                    """
                        .formatted(original),
                    true,
                    true))
            .willReturn(fhirResponse(generator.generateString())));
  }

  /**
   * Configures standard gPAS mocks for transport mapping tests. Includes metadata endpoint and
   * three parallel pseudonymization calls for patient ID, salt, and date shift.
   */
  protected void configureStandardGpasMocks() throws IOException {
    configureGpasMetadataMock();
    configureGpasPseudonymizationMock("patient-id-1", "pseudonym-123");
    configureGpasPseudonymizationMock("Salt_patient-id-1", "salt-pseudonym-123");
    configureGpasPseudonymizationMock("PT336H_patient-id-1", "dateshift-seed-456");
  }

  /**
   * Executes a POST request to TCA and verifies the response.
   *
   * @param path The endpoint path
   * @param request The request body
   * @param uriFunction Optional URI builder function for query parameters
   * @param verifier Consumer to verify the response bundle
   */
  protected void executePostRequest(
      String path,
      Object request,
      Function<UriBuilder, URI> uriFunction,
      Consumer<Bundle> verifier) {
    var webClient = createTcaWebClient();
    var requestSpec = webClient.post();
    var response =
        (uriFunction != null
                ? requestSpec.uri(uriBuilder -> uriFunction.apply(uriBuilder.path(path)))
                : requestSpec.uri(path))
            .header("Content-Type", "application/json")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(Bundle.class);

    StepVerifier.create(response)
        .assertNext(
            bundle -> {
              log.info("Received bundle with {} entries", bundle.getEntry().size());
              verifier.accept(bundle);
            })
        .verifyComplete();
  }

  /**
   * Verifies that a bundle has the nested structure expected from TCA consent endpoints. Each entry
   * should be a Bundle containing at least one Patient resource.
   */
  protected void verifyNestedBundleStructure(Bundle bundle) {
    assertThat(bundle).isNotNull();
    assertThat(bundle.getEntry()).isNotEmpty();

    bundle
        .getEntry()
        .forEach(
            entry -> {
              assertThat(entry.getResource()).isNotNull();
              assertThat(entry.getResource()).isInstanceOf(Bundle.class);

              Bundle innerBundle = (Bundle) entry.getResource();
              assertThat(innerBundle.getEntry()).isNotEmpty();

              // Verify inner bundle contains at least one Patient resource
              boolean hasPatient =
                  innerBundle.getEntry().stream()
                      .anyMatch(e -> e.getResource().getResourceType().name().equals("Patient"));
              assertThat(hasPatient).isTrue();
            });
  }
}
