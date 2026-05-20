package care.smith.fts.tca.selfassessment;

import static care.smith.fts.util.selfassessment.Probes.DEFAULT_TIMEOUT;
import static care.smith.fts.util.selfassessment.Probes.probeFhirCapability;
import static care.smith.fts.util.selfassessment.Probes.probeGet;
import static java.util.Objects.requireNonNullElse;

import care.smith.fts.tca.consent.GicsFhirUtil;
import care.smith.fts.tca.deidentification.configuration.GpasDeIdentificationConfiguration;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.selfassessment.ComponentStatus;
import care.smith.fts.util.selfassessment.SelfAssessmentReport;
import care.smith.fts.util.selfassessment.Status;
import care.smith.fts.util.selfassessment.StatusAggregator;
import jakarta.annotation.Nullable;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class SelfAssessmentService {

  private static final String AGENT = "trust-center-agent";

  private final WebClient gpasClient;
  private final String gpasBaseUrl;
  private final @Nullable WebClient gicsClient;
  private final String gicsBaseUrl;
  private final RedissonClient redisClient;
  private final String keystoreUrl;
  private final String oauth2IssuerUri;
  private final WebClientFactory webClientFactory;
  private final Duration timeout;

  public SelfAssessmentService(
      @Qualifier("gpasFhirHttpClient") WebClient gpasClient,
      GpasDeIdentificationConfiguration gpasCfg,
      @Nullable @Qualifier("gicsFhirHttpClient") WebClient gicsClient,
      @Value("${consent.gics.fhir.baseUrl:}") String gicsBaseUrl,
      RedissonClient redisClient,
      @Value("${de-identification.keystoreUrl:}") String keystoreUrl,
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String oauth2IssuerUri,
      WebClientFactory webClientFactory,
      @Value("${selfassessment.timeout:PT3S}") Duration timeout) {
    this.gpasClient = gpasClient;
    this.gpasBaseUrl = gpasCfg.getFhir().baseUrl();
    this.gicsClient = gicsClient;
    this.gicsBaseUrl = gicsBaseUrl;
    this.redisClient = redisClient;
    this.keystoreUrl = keystoreUrl;
    this.oauth2IssuerUri = oauth2IssuerUri;
    this.webClientFactory = webClientFactory;
    this.timeout = requireNonNullElse(timeout, DEFAULT_TIMEOUT);
  }

  public Mono<SelfAssessmentReport> assess() {
    return Mono.zip(probeGpas(), probeGics(), probeRedis(), probeOauth2())
        .map(
            t -> {
              var components = List.of(t.getT1(), t.getT2(), t.getT3(), t.getT4());
              var overall =
                  StatusAggregator.worstOf(components.stream().map(ComponentStatus::status));
              return new SelfAssessmentReport(AGENT, overall, components, List.of());
            });
  }

  private Mono<ComponentStatus> probeGpas() {
    return probeFhirCapability(
        gpasClient,
        "gpas",
        gpasBaseUrl,
        timeout,
        GpasDeIdentificationConfiguration.GPAS_OPERATIONS);
  }

  private Mono<ComponentStatus> probeGics() {
    if (gicsClient == null) {
      return Mono.just(ComponentStatus.skipped("gics", "fhir", "not configured"));
    }
    return probeFhirCapability(
        gicsClient, "gics", gicsBaseUrl, timeout, GicsFhirUtil.GICS_OPERATIONS);
  }

  private Mono<ComponentStatus> probeRedis() {
    long start = System.nanoTime();
    return redisClient
        .reactive()
        .getBucket("__selfassessment_probe__")
        .isExists()
        .map(
            ignored ->
                ComponentStatus.up(
                    "redis", "redis", keystoreUrl, (System.nanoTime() - start) / 1_000_000L))
        .timeout(timeout)
        .onErrorResume(
            e -> Mono.just(ComponentStatus.down("redis", "redis", keystoreUrl, errorReason(e))));
  }

  private Mono<ComponentStatus> probeOauth2() {
    if (oauth2IssuerUri == null || oauth2IssuerUri.isBlank()) {
      return Mono.just(ComponentStatus.skipped("keycloak", "oauth2", "not configured"));
    }
    var client = webClientFactory.create(new HttpClientConfig(oauth2IssuerUri));
    return probeGet(
        client,
        "/.well-known/openid-configuration",
        "keycloak",
        "oauth2",
        oauth2IssuerUri,
        timeout);
  }

  private static String errorReason(Throwable e) {
    return Objects.toString(e.getMessage(), e.getClass().getSimpleName());
  }

  Status overallStatus(List<ComponentStatus> components) {
    return StatusAggregator.worstOf(components.stream().map(ComponentStatus::status));
  }
}
