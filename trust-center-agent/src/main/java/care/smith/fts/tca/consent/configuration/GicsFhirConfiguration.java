package care.smith.fts.tca.consent.configuration;

import care.smith.fts.tca.consent.FhirConsentProvider;
import care.smith.fts.tca.consent.PolicyHandler;
import care.smith.fts.util.HTTPClientConfig;
import care.smith.fts.util.auth.HTTPClientAuthMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "consent.gics.fhir")
@Data
public class GicsFhirConfiguration {
  @NotBlank String baseUrl;
  int pageSize = 100;
  @NotNull HTTPClientAuthMethod.AuthMethod auth;

  @Bean
  int pageSize() {
    return pageSize;
  }

  @Bean
  FhirConsentProvider fhirConsentProvider(
      PolicyHandler policyHandler, ConsentProviderConfiguration consentProviderConfiguration) {
    HTTPClientConfig httpClientConfig = new HTTPClientConfig(baseUrl, auth);
    var client = httpClientConfig.createClient(HttpClientBuilder.create());
    return new FhirConsentProvider(
        client,
        policyHandler,
        consentProviderConfiguration.policySystem,
        consentProviderConfiguration.patientIdentifierSystem,
        pageSize);
  }
}
