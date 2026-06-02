package care.smith.fts.util;

import static java.time.Duration.ofSeconds;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.util.auth.HttpServerAuthConfig;
import care.smith.fts.util.auth.OAuth2ConfigurationExistsCondition;
import care.smith.fts.util.fhir.FhirCodecConfiguration;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.TimeZone;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.AuthorizedClientServiceReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;

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

  @Bean
  public FhirContext fhirContext() {
    return FhirContext.forR4();
  }

  @Bean
  public RetryStrategy retryStrategy(MeterRegistry meterRegistry) {
    return new DefaultRetryStrategy(meterRegistry);
  }

  /**
   * Pooled JDK HTTP client used by every agent's outbound WebClient.
   *
   * <p>JDK HttpClient exposes no builder API for connection-pool keep-alive; the only knob is the
   * {@code jdk.httpclient.keepalive.timeout} system property. We set it here so the pool evicts
   * cached connections before any upstream server closes them on its own idle timeout. The value
   * must stay below the lowest idle timeout among the agent's upstreams; if client keep-alive ≥
   * server idle, the pool reuses a half-dead socket and the first request after an idle gap fails
   * with "HTTP/1.1 header parser received no bytes" / {@code EOFException}.
   *
   * <p>Default 25s targets the tightest upstream we know about: Samply Blaze (Jetty, 30s idle),
   * reached by cd-agent and rd-agent via cd-hds / rd-hds. tc-agent talks to gICS and gPAS
   * (WildFly/Undertow) and serves inbound calls from cd-/rd-agent itself, so the same pool covers
   * those flows too. See {@code docs/configuration/http-client.md}.
   */
  @Bean
  public HttpClient httpClient(
      @Value("${fts.http.client.keepalive-timeout:PT25S}") Duration keepaliveTimeout) {
    System.setProperty(
        "jdk.httpclient.keepalive.timeout", String.valueOf(keepaliveTimeout.toSeconds()));
    return HttpClient.newBuilder().connectTimeout(ofSeconds(10)).build();
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
