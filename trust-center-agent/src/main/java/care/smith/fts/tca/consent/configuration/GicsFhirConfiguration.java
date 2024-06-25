package care.smith.fts.tca.consent.configuration;

import care.smith.fts.tca.consent.FhirConsentProvider;
import care.smith.fts.tca.consent.PolicyHandler;
import care.smith.fts.util.HTTPClientConfig;
import care.smith.fts.util.auth.HTTPClientAuthMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@ConfigurationProperties(prefix = "consent.gics.fhir")
@Data
public class GicsFhirConfiguration {
  @NotBlank String baseUrl;
  int defaultPageSize = 100;
  @NotNull HTTPClientAuthMethod.AuthMethod auth;

  @Bean
  int defaultPageSize() {
    return defaultPageSize;
  }

  @Bean("gicsFhirHttpClient")
  public WebClient httpClient(WebClient.Builder builder) {
    HTTPClientConfig httpClientConfig = new HTTPClientConfig(baseUrl, auth);
    return httpClientConfig.createClient(builder);
  }

  @Bean
  FhirConsentProvider fhirConsentProvider(PolicyHandler policyHandler, WebClient.Builder builder) {
    HTTPClientConfig httpClientConfig = new HTTPClientConfig(baseUrl, auth);
    var client = httpClientConfig.createClient(builder);
    return new FhirConsentProvider(client, policyHandler, defaultPageSize);
  }
}
