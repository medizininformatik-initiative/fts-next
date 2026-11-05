package care.smith.fts.util.selfassessment;

import static care.smith.fts.util.selfassessment.Probes.probeActuatorHealth;
import static care.smith.fts.util.selfassessment.Probes.probeFhirCapability;
import static care.smith.fts.util.selfassessment.Probes.probeGet;
import static care.smith.fts.util.selfassessment.Probes.probeReachable;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.util.fhir.FhirDecoder;
import care.smith.fts.util.fhir.FhirEncoder;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

class ProbesTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final FhirContext FHIR = FhirContext.forR4();
  private static final String FHIR_CAPABILITY_JSON =
      """
      {
        "resourceType": "CapabilityStatement",
        "rest": [
          { "operation": [ { "name": "$op-required" }, { "name": "$op-extra" } ] }
        ]
      }
      """;

  @RegisterExtension static WireMockExtension wm = WireMockExtension.newInstance().build();

  private static WebClient fhirClient(WireMockRuntimeInfo info) {
    return WebClient.builder()
        .baseUrl(info.getHttpBaseUrl())
        .codecs(c -> c.customCodecs().register(new FhirDecoder(FHIR)))
        .codecs(c -> c.customCodecs().register(new FhirEncoder(FHIR)))
        .build();
  }

  private static WebClient jsonClient(WireMockRuntimeInfo info) {
    return WebClient.builder().baseUrl(info.getHttpBaseUrl()).build();
  }

  @Test
  void fhirCapabilityUpWhenAllOpsPresent() {
    wm.stubFor(
        get(urlPathEqualTo("/metadata"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/fhir+json")
                    .withBody(FHIR_CAPABILITY_JSON)));
    StepVerifier.create(
            probeFhirCapability(
                fhirClient(wm.getRuntimeInfo()),
                "gpas",
                "http://x",
                TIMEOUT,
                List.of("$op-required")))
        .assertNext(
            s -> {
              assertThat(s.name()).isEqualTo("gpas");
              assertThat(s.kind()).isEqualTo("fhir");
              assertThat(s.status()).isEqualTo(Status.UP);
              assertThat(s.latencyMs()).isNotNull();
              assertThat(s.reason()).isNull();
            })
        .verifyComplete();
  }

  @Test
  void fhirCapabilityUpWhenNoOpsRequired() {
    wm.stubFor(
        get(urlPathEqualTo("/metadata"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/fhir+json")
                    .withBody(FHIR_CAPABILITY_JSON)));
    StepVerifier.create(
            probeFhirCapability(
                fhirClient(wm.getRuntimeInfo()), "gics", "http://x", TIMEOUT, List.of()))
        .assertNext(s -> assertThat(s.status()).isEqualTo(Status.UP))
        .verifyComplete();
  }

  @Test
  void fhirCapabilityUpWhenRequiredOpsNull() {
    wm.stubFor(
        get(urlPathEqualTo("/metadata"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/fhir+json")
                    .withBody(FHIR_CAPABILITY_JSON)));
    StepVerifier.create(
            probeFhirCapability(fhirClient(wm.getRuntimeInfo()), "gics", "http://x", TIMEOUT, null))
        .assertNext(s -> assertThat(s.status()).isEqualTo(Status.UP))
        .verifyComplete();
  }

  @Test
  void fhirCapabilityDegradedWhenOpMissing() {
    wm.stubFor(
        get(urlPathEqualTo("/metadata"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/fhir+json")
                    .withBody(FHIR_CAPABILITY_JSON)));
    StepVerifier.create(
            probeFhirCapability(
                fhirClient(wm.getRuntimeInfo()), "gpas", "http://x", TIMEOUT, List.of("$missing")))
        .assertNext(
            s -> {
              assertThat(s.status()).isEqualTo(Status.DEGRADED);
              assertThat(s.reason()).contains("$missing");
            })
        .verifyComplete();
  }

  @Test
  void fhirCapabilityDownOn5xx() {
    wm.stubFor(
        get(urlPathEqualTo("/metadata")).willReturn(aResponse().withStatus(500).withBody("boom")));
    StepVerifier.create(
            probeFhirCapability(
                fhirClient(wm.getRuntimeInfo()), "gpas", "http://x", TIMEOUT, List.of()))
        .assertNext(
            s -> {
              assertThat(s.status()).isEqualTo(Status.DOWN);
              assertThat(s.reason()).isNotEmpty();
            })
        .verifyComplete();
  }

  @Test
  void fhirCapabilityDownOnTimeout() {
    wm.stubFor(
        get(urlPathEqualTo("/metadata"))
            .willReturn(
                aResponse()
                    .withFixedDelay(500)
                    .withHeader("Content-Type", "application/fhir+json")
                    .withBody(FHIR_CAPABILITY_JSON)));
    StepVerifier.create(
            probeFhirCapability(
                fhirClient(wm.getRuntimeInfo()),
                "gpas",
                "http://x",
                Duration.ofMillis(50),
                List.of()))
        .assertNext(s -> assertThat(s.status()).isEqualTo(Status.DOWN))
        .verifyComplete();
  }

  @Test
  void actuatorHealthUp() {
    wm.stubFor(
        get(urlPathEqualTo("/actuator/health"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"status\":\"UP\"}")));
    StepVerifier.create(
            probeActuatorHealth(
                jsonClient(wm.getRuntimeInfo()), "tca", "rd-agent", "http://x", TIMEOUT))
        .assertNext(s -> assertThat(s.status()).isEqualTo(Status.UP))
        .verifyComplete();
  }

  @Test
  void actuatorHealthDegradedWhenStatusNotUp() {
    wm.stubFor(
        get(urlPathEqualTo("/actuator/health"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"status\":\"OUT_OF_SERVICE\"}")));
    StepVerifier.create(
            probeActuatorHealth(
                jsonClient(wm.getRuntimeInfo()), "tca", "rd-agent", "http://x", TIMEOUT))
        .assertNext(
            s -> {
              assertThat(s.status()).isEqualTo(Status.DEGRADED);
              assertThat(s.reason()).contains("OUT_OF_SERVICE");
            })
        .verifyComplete();
  }

  @Test
  void actuatorHealthDownOnError() {
    wm.stubFor(get(urlPathEqualTo("/actuator/health")).willReturn(aResponse().withStatus(500)));
    StepVerifier.create(
            probeActuatorHealth(
                jsonClient(wm.getRuntimeInfo()), "tca", "rd-agent", "http://x", TIMEOUT))
        .assertNext(s -> assertThat(s.status()).isEqualTo(Status.DOWN))
        .verifyComplete();
  }

  @Test
  void probeGetUpOn2xx() {
    wm.stubFor(
        get(urlPathEqualTo("/.well-known/openid-configuration"))
            .willReturn(aResponse().withStatus(200)));
    StepVerifier.create(
            probeGet(
                jsonClient(wm.getRuntimeInfo()),
                "/.well-known/openid-configuration",
                "keycloak",
                "oauth2",
                "http://x",
                TIMEOUT))
        .assertNext(s -> assertThat(s.status()).isEqualTo(Status.UP))
        .verifyComplete();
  }

  @Test
  void probeGetDownOnError() {
    wm.stubFor(get(urlPathEqualTo("/x")).willReturn(aResponse().withStatus(500)));
    StepVerifier.create(
            probeGet(
                jsonClient(wm.getRuntimeInfo()), "/x", "keycloak", "oauth2", "http://x", TIMEOUT))
        .assertNext(s -> assertThat(s.status()).isEqualTo(Status.DOWN))
        .verifyComplete();
  }

  @Test
  void probeReachableUpOn2xx() {
    wm.stubFor(get(urlPathEqualTo("/")).willReturn(aResponse().withStatus(200)));
    StepVerifier.create(
            probeReachable(
                jsonClient(wm.getRuntimeInfo()), "downstream", "http", "http://x", TIMEOUT))
        .assertNext(
            s -> {
              assertThat(s.status()).isEqualTo(Status.UP);
              assertThat(s.kind()).isEqualTo("http");
              assertThat(s.latencyMs()).isNotNull();
            })
        .verifyComplete();
  }

  @Test
  void probeReachableUpOn4xx() {
    wm.stubFor(get(urlPathEqualTo("/")).willReturn(aResponse().withStatus(401)));
    StepVerifier.create(
            probeReachable(
                jsonClient(wm.getRuntimeInfo()), "downstream", "http", "http://x", TIMEOUT))
        .assertNext(s -> assertThat(s.status()).isEqualTo(Status.UP))
        .verifyComplete();
  }

  @Test
  void probeReachableDownOnConnectionRefused() {
    var dead = WebClient.builder().baseUrl("http://localhost:1").build();
    StepVerifier.create(probeReachable(dead, "downstream", "http", "http://x", TIMEOUT))
        .assertNext(
            s -> {
              assertThat(s.status()).isEqualTo(Status.DOWN);
              assertThat(s.reason()).isNotEmpty();
            })
        .verifyComplete();
  }

  @Test
  void probeReachableDownOnTimeoutFallsBackToClassName() {
    wm.stubFor(
        get(urlPathEqualTo("/")).willReturn(aResponse().withFixedDelay(500).withStatus(200)));
    StepVerifier.create(
            probeReachable(
                jsonClient(wm.getRuntimeInfo()),
                "downstream",
                "http",
                "http://x",
                Duration.ofMillis(50)))
        .assertNext(
            s -> {
              assertThat(s.status()).isEqualTo(Status.DOWN);
              assertThat(s.reason()).isNotEmpty();
            })
        .verifyComplete();
  }
}
