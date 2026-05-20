package care.smith.fts.tca.selfassessment;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.tca.deidentification.configuration.GpasDeIdentificationConfiguration;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.fhir.FhirDecoder;
import care.smith.fts.util.fhir.FhirEncoder;
import care.smith.fts.util.selfassessment.Status;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.redisson.api.RedissonClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class SelfAssessmentServiceTest {

  private static final FhirContext FHIR = FhirContext.forR4();
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  private static final String GPAS_CAPABILITY_OK =
      """
      {"resourceType":"CapabilityStatement",
       "rest":[{"operation":[{"name":"pseudonymizeAllowCreate"}]}]}
      """;
  private static final String GICS_CAPABILITY_OK =
      """
      {"resourceType":"CapabilityStatement",
       "rest":[{"operation":[
         {"name":"allConsentsForDomain"},
         {"name":"allConsentsForPerson"}]}]}
      """;

  @RegisterExtension static WireMockExtension gpas = WireMockExtension.newInstance().build();
  @RegisterExtension static WireMockExtension gics = WireMockExtension.newInstance().build();
  @RegisterExtension static WireMockExtension oauth = WireMockExtension.newInstance().build();

  private static WebClient fhirClient(String baseUrl) {
    return WebClient.builder()
        .baseUrl(baseUrl)
        .codecs(c -> c.customCodecs().register(new FhirDecoder(FHIR)))
        .codecs(c -> c.customCodecs().register(new FhirEncoder(FHIR)))
        .build();
  }

  private static GpasDeIdentificationConfiguration gpasConfig() {
    var cfg = new GpasDeIdentificationConfiguration();
    cfg.setFhir(new HttpClientConfig("http://gpas:8080"));
    return cfg;
  }

  private static RedissonClient redisUp() {
    var rc = mock(RedissonClient.class, RETURNS_DEEP_STUBS);
    when(rc.reactive().getBucket("__selfassessment_probe__").isExists())
        .thenReturn(Mono.just(false));
    return rc;
  }

  private static RedissonClient redisDown() {
    var rc = mock(RedissonClient.class, RETURNS_DEEP_STUBS);
    when(rc.reactive().getBucket("__selfassessment_probe__").isExists())
        .thenReturn(Mono.error(new RuntimeException("connection refused")));
    return rc;
  }

  @Test
  void allUp_whenGpasGicsRedisOauthAllReachable() {
    gpas.stubFor(
        get(urlPathEqualTo("/metadata"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/fhir+json")
                    .withBody(GPAS_CAPABILITY_OK)));
    gics.stubFor(
        get(urlPathEqualTo("/metadata"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/fhir+json")
                    .withBody(GICS_CAPABILITY_OK)));
    var svc =
        new SelfAssessmentService(
            fhirClient(gpas.getRuntimeInfo().getHttpBaseUrl()),
            gpasConfig(),
            fhirClient(gics.getRuntimeInfo().getHttpBaseUrl()),
            gics.getRuntimeInfo().getHttpBaseUrl(),
            redisUp(),
            "redis://keystore:6379",
            "",
            mock(WebClientFactory.class),
            TIMEOUT);

    StepVerifier.create(svc.assess())
        .assertNext(
            r -> {
              assertThat(r.agent()).isEqualTo("trust-center-agent");
              assertThat(r.overall()).isEqualTo(Status.UP);
              assertThat(r.components()).hasSize(4);
              assertThat(r.components())
                  .extracting("name")
                  .containsExactly("gpas", "gics", "redis", "keycloak");
            })
        .verifyComplete();
  }

  @Test
  void overallDown_whenGpasDown() {
    gpas.stubFor(get(urlPathEqualTo("/metadata")).willReturn(aResponse().withStatus(500)));
    var svc =
        new SelfAssessmentService(
            fhirClient(gpas.getRuntimeInfo().getHttpBaseUrl()),
            gpasConfig(),
            null,
            "",
            redisUp(),
            "redis://keystore:6379",
            "",
            mock(WebClientFactory.class),
            TIMEOUT);

    StepVerifier.create(svc.assess())
        .assertNext(
            r -> {
              assertThat(r.overall()).isEqualTo(Status.DOWN);
              assertThat(r.components().get(0).status()).isEqualTo(Status.DOWN);
            })
        .verifyComplete();
  }

  @Test
  void gicsSkipped_whenClientNull() {
    gpas.stubFor(
        get(urlPathEqualTo("/metadata"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/fhir+json")
                    .withBody(GPAS_CAPABILITY_OK)));
    var svc =
        new SelfAssessmentService(
            fhirClient(gpas.getRuntimeInfo().getHttpBaseUrl()),
            gpasConfig(),
            null,
            "",
            redisUp(),
            "redis://keystore:6379",
            "",
            mock(WebClientFactory.class),
            TIMEOUT);

    StepVerifier.create(svc.assess())
        .assertNext(
            r -> {
              var gicsComp =
                  r.components().stream()
                      .filter(c -> "gics".equals(c.name()))
                      .findFirst()
                      .orElseThrow();
              assertThat(gicsComp.status()).isEqualTo(Status.SKIPPED);
              assertThat(r.overall()).isEqualTo(Status.UP);
            })
        .verifyComplete();
  }

  @Test
  void oauth2ConfiguredAndReachable_keycloakUp() {
    gpas.stubFor(
        get(urlPathEqualTo("/metadata"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/fhir+json")
                    .withBody(GPAS_CAPABILITY_OK)));
    oauth.stubFor(
        get(urlPathEqualTo("/.well-known/openid-configuration"))
            .willReturn(aResponse().withStatus(200)));
    var oauthUrl = oauth.getRuntimeInfo().getHttpBaseUrl();
    var factory = mock(WebClientFactory.class);
    when(factory.create(any(HttpClientConfig.class)))
        .thenReturn(WebClient.builder().baseUrl(oauthUrl).build());

    var svc =
        new SelfAssessmentService(
            fhirClient(gpas.getRuntimeInfo().getHttpBaseUrl()),
            gpasConfig(),
            null,
            "",
            redisUp(),
            "redis://keystore:6379",
            oauthUrl,
            factory,
            TIMEOUT);

    StepVerifier.create(svc.assess())
        .assertNext(
            r -> {
              var keycloak =
                  r.components().stream()
                      .filter(c -> "keycloak".equals(c.name()))
                      .findFirst()
                      .orElseThrow();
              assertThat(keycloak.status()).isEqualTo(Status.UP);
            })
        .verifyComplete();
  }

  @Test
  void oauth2IssuerNull_keycloakSkipped() {
    gpas.stubFor(
        get(urlPathEqualTo("/metadata"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/fhir+json")
                    .withBody(GPAS_CAPABILITY_OK)));
    var svc =
        new SelfAssessmentService(
            fhirClient(gpas.getRuntimeInfo().getHttpBaseUrl()),
            gpasConfig(),
            null,
            "",
            redisUp(),
            "redis://keystore:6379",
            null,
            mock(WebClientFactory.class),
            TIMEOUT);

    StepVerifier.create(svc.assess())
        .assertNext(
            r -> {
              var keycloak =
                  r.components().stream()
                      .filter(c -> "keycloak".equals(c.name()))
                      .findFirst()
                      .orElseThrow();
              assertThat(keycloak.status()).isEqualTo(Status.SKIPPED);
            })
        .verifyComplete();
  }

  @Test
  void redisErrorWithoutMessage_reasonFallsBackToClassName() {
    gpas.stubFor(
        get(urlPathEqualTo("/metadata"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/fhir+json")
                    .withBody(GPAS_CAPABILITY_OK)));
    var rc = mock(RedissonClient.class, RETURNS_DEEP_STUBS);
    when(rc.reactive().getBucket("__selfassessment_probe__").isExists())
        .thenReturn(Mono.error(new RuntimeException()));

    var svc =
        new SelfAssessmentService(
            fhirClient(gpas.getRuntimeInfo().getHttpBaseUrl()),
            gpasConfig(),
            null,
            "",
            rc,
            "redis://keystore:6379",
            "",
            mock(WebClientFactory.class),
            TIMEOUT);

    StepVerifier.create(svc.assess())
        .assertNext(
            r -> {
              var redis =
                  r.components().stream()
                      .filter(c -> "redis".equals(c.name()))
                      .findFirst()
                      .orElseThrow();
              assertThat(redis.status()).isEqualTo(Status.DOWN);
              assertThat(redis.reason()).isEqualTo("RuntimeException");
            })
        .verifyComplete();
  }

  @Test
  void overallStatusHelperDelegatesToAggregator() {
    var svc =
        new SelfAssessmentService(
            fhirClient(gpas.getRuntimeInfo().getHttpBaseUrl()),
            gpasConfig(),
            null,
            "",
            redisUp(),
            "redis://keystore:6379",
            "",
            mock(WebClientFactory.class),
            TIMEOUT);
    var components =
        java.util.List.of(
            care.smith.fts.util.selfassessment.ComponentStatus.up("a", "http", "http://a", 1L),
            care.smith.fts.util.selfassessment.ComponentStatus.down("b", "http", "http://b", "x"));
    assertThat(svc.overallStatus(components)).isEqualTo(Status.DOWN);
  }

  @Test
  void redisDown_overallDown() {
    gpas.stubFor(
        get(urlPathEqualTo("/metadata"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/fhir+json")
                    .withBody(GPAS_CAPABILITY_OK)));
    var svc =
        new SelfAssessmentService(
            fhirClient(gpas.getRuntimeInfo().getHttpBaseUrl()),
            gpasConfig(),
            null,
            "",
            redisDown(),
            "redis://keystore:6379",
            "",
            mock(WebClientFactory.class),
            TIMEOUT);

    StepVerifier.create(svc.assess())
        .assertNext(
            r -> {
              assertThat(r.overall()).isEqualTo(Status.DOWN);
              var redis =
                  r.components().stream()
                      .filter(c -> "redis".equals(c.name()))
                      .findFirst()
                      .orElseThrow();
              assertThat(redis.status()).isEqualTo(Status.DOWN);
              assertThat(redis.reason()).contains("connection refused");
            })
        .verifyComplete();
  }
}
