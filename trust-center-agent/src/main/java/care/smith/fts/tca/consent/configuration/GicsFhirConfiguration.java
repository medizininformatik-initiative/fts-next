package care.smith.fts.tca.consent.configuration;

import care.smith.fts.tca.consent.FhirConsentedPatientsProvider;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.auth.HttpClientAuth;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@ConfigurationProperties(prefix = "consent.gics.fhir")
@Data
public class GicsFhirConfiguration {
  private @NotBlank String baseUrl;
  private HttpClientAuth.Config auth;
  private HttpClientConfig.Ssl ssl;
  private int defaultPageSize = 50;

  @Bean
  int defaultPageSize() {
    return defaultPageSize;
  }

  @Bean("gicsFhirHttpClient")
  public WebClient httpClient(WebClientFactory clientFactory) {
    var config = new HttpClientConfig(baseUrl, auth, ssl);
    return clientFactory.create(config);
  }

  @Bean
  FhirConsentedPatientsProvider fhirConsentedPatientsProvider(
      WebClientFactory clientFactory, MeterRegistry meterRegistry) {
    var config = new HttpClientConfig(baseUrl, auth, ssl);
    var client = clientFactory.create(config);
    return new FhirConsentedPatientsProvider(client, meterRegistry);
  }
}
