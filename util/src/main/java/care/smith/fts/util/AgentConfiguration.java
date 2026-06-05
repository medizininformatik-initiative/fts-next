package care.smith.fts.util;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.util.auth.HttpServerAuthConfig;
import care.smith.fts.util.auth.OAuth2ConfigurationExistsCondition;
import care.smith.fts.util.fhir.FhirCodecConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.TimeZone;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.ReactorResourceFactory;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import reactor.netty.resources.ConnectionProvider;

@Configuration
@Import({
  WebClientDefaults.class,
  FhirCodecConfiguration.class,
  MetricsConfig.class,
  HttpServerAuthConfig.class,
  CustomErrorHandler.class,
  WebClientFactory.class,
})
public class AgentConfiguration {

  /**
   * Per-host outbound concurrency budget. The transfer pipeline's highest-fan-out stages (patient
   * select, de-identify) dispatch up to this many concurrent requests onto a single upstream host;
   * the shared connection pool is capped at the same value (see {@link #ftsClientResources}) so the
   * transport never throttles the pipeline below its dispatch rate. Kept as one constant,
   * referenced by both the pool cap and the pipeline {@code flatMap} concurrency, so the two cannot
   * drift.
   */
  public static final int MAX_OUTBOUND_FANOUT = 256;

  @Bean
  public FhirContext fhirContext() {
    return FhirContext.forR4();
  }

  @Bean
  public RetryStrategy retryStrategy(MeterRegistry meterRegistry) {
    return new DefaultRetryStrategy(meterRegistry);
  }

  /**
   * Reactor Netty connection-pool resources shared by every agent's outbound WebClient. Spring Boot
   * feeds this {@link ReactorResourceFactory} into the auto-detected Reactor Netty connector, so
   * the same pool backs both plain and SSL/mTLS clients.
   *
   * <p>Unlike the JDK client's single JVM-global {@code jdk.httpclient.keepalive.timeout} knob, the
   * pool is configured programmatically: {@code maxIdleTime} evicts connections idle longer than
   * the configured duration, and a pooled channel sits on an event loop so a server FIN evicts it
   * before reuse. Together these largely remove the "reuse a half-dead socket → EOF after an idle
   * gap" failure mode without under-tuning a global timeout. {@code maxLifeTime} caps total
   * connection age and {@code evictInBackground} sweeps expired connections without waiting for
   * traffic.
   *
   * <p>{@code maxIdleTime} should still stay below the tightest upstream idle timeout (e.g. Samply
   * Blaze defaults to 30s). See {@code docs/configuration/http-client.md}.
   *
   * <p>{@code maxConnections} caps in-flight connections <em>per upstream host</em> at {@link
   * #MAX_OUTBOUND_FANOUT}, the same value the pipeline fans out at, so the pool never throttles a
   * transfer below its dispatch rate. Left unset, the pool would fall back to reactor-netty's
   * library default ({@code max(cores,8)*2}, &ge;16) and reject or stall bursts. {@code
   * pendingAcquireTimeout} is aligned with the request-timeout filter so a saturated pool fails
   * fast with a meaningful pool error rather than the generic 45s default.
   *
   * <p>Marked {@code @Primary} because Spring Boot still auto-configures its own {@code
   * reactorResourceFactory}; without a primary, the Netty server's single-typed injection of {@code
   * ReactorResourceFactory} is ambiguous. With {@code @Primary}, both the outbound client connector
   * and the Netty server resolve to this instance. The connection pool is only exercised by
   * outbound clients; the server merely shares its event-loop resources.
   */
  @Bean
  @Primary
  public ReactorResourceFactory ftsClientResources(
      @Value("${fts.http.client.max-idle-time:PT25S}") Duration maxIdleTime) {
    var connectionProvider =
        ConnectionProvider.builder("fts-http-client")
            .maxConnections(MAX_OUTBOUND_FANOUT)
            .pendingAcquireTimeout(Duration.ofSeconds(10))
            .maxIdleTime(maxIdleTime)
            .maxLifeTime(Duration.ofMinutes(5))
            .evictInBackground(Duration.ofSeconds(30))
            .build();
    var resourceFactory = new ReactorResourceFactory();
    resourceFactory.setUseGlobalResources(false);
    resourceFactory.setConnectionProvider(connectionProvider);
    return resourceFactory;
  }

  @Bean
  @Primary
  public ObjectMapper defaultObjectMapper(
      @Value("${spring.jackson.time-zone:UTC}") String timeZone) {
    return new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setTimeZone(TimeZone.getTimeZone(timeZone));
  }

  @Bean
  @Conditional(OAuth2ConfigurationExistsCondition.class)
  public ReactiveOAuth2AuthorizedClientManager authorizedClientManager(
      ReactiveClientRegistrationRepository clientRegistrationRepository,
      ReactiveOAuth2AuthorizedClientService authorizedClientService) {
    var authorizedClientManager =
        new AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager(
            clientRegistrationRepository, authorizedClientService);

    var authorizedClientProvider =
        ReactiveOAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build();

    authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);
    return authorizedClientManager;
  }
}
