package care.smith.fts.tca.consent.configuration;

import care.smith.fts.tca.consent.FhirConsentedPatientsProvider;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.auth.HttpClientAuthMethod;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientSsl;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.Builder;

@Configuration
@ConfigurationProperties(prefix = "consent.gics.fhir")
@Data
public class GicsFhirConfiguration {
  @NotBlank String baseUrl;
  int defaultPageSize = 50;
  @NotNull HttpClientAuthMethod.AuthMethod auth = HttpClientAuthMethod.AuthMethod.NONE;

  @Bean
  int defaultPageSize() {
    return defaultPageSize;
  }

  @Bean("gicsFhirHttpClient")
  public WebClient httpClient(WebClient.Builder builder, WebClientSsl ssl) {
    HttpClientConfig httpClientConfig = new HttpClientConfig(baseUrl, auth);
    return httpClientConfig.createClient(builder, ssl);
  }

  @Bean
  FhirConsentedPatientsProvider fhirConsentedPatientsProvider(
      Builder builder, MeterRegistry meterRegistry, WebClientSsl ssl) {
    HttpClientConfig httpClientConfig = new HttpClientConfig(baseUrl, auth);
    var client = httpClientConfig.createClient(builder, ssl);
    return new FhirConsentedPatientsProvider(client, meterRegistry);
  }
}
