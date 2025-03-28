package care.smith.fts.util;

import static java.time.Duration.ofSeconds;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.util.auth.HttpServerAuthConfig;
import care.smith.fts.util.auth.OAuth2ConfigurationExistsCondition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.http.HttpClient;
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
  public HttpClient httpClient() {
    return HttpClient.newBuilder().connectTimeout(ofSeconds(10)).build();
  }

  @Bean
  @Primary
  public ObjectMapper defaultObjectMapper() {
    return new ObjectMapper().registerModule(new JavaTimeModule());
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
