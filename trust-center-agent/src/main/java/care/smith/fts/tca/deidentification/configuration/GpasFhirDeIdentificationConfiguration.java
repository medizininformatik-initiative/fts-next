package care.smith.fts.tca.deidentification.configuration;

import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.auth.HttpClientAuth;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@ConfigurationProperties(prefix = "de-identification.gpas.fhir")
@Data
public class GpasFhirDeIdentificationConfiguration {
  @NotBlank String baseUrl;
  HttpClientAuth.Config auth;

  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @Bean("gpasFhirHttpClient")
  public WebClient httpClient(WebClientFactory clientFactory) {
    return clientFactory.create(new HttpClientConfig(baseUrl, auth));
  }

  @Bean
  public RandomGenerator secureRandom() {
    return new SecureRandom();
  }
}
