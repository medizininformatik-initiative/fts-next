package care.smith.fts.tca.deidentification.configuration;

import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.auth.HttpClientAuthMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import lombok.Data;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientSsl;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@ConfigurationProperties(prefix = "de-identification.gpas.fhir")
@Data
public class GpasFhirDeIdentificationConfiguration {
  @NotBlank String baseUrl;
  @NotNull HttpClientAuthMethod.AuthMethod auth = HttpClientAuthMethod.AuthMethod.NONE;

  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @Bean("gpasFhirHttpClient")
  public WebClient httpClient(WebClient.Builder builder, WebClientSsl ssl) {
    HttpClientConfig httpClientConfig = new HttpClientConfig(baseUrl, auth);
    return httpClientConfig.createClient(builder, ssl);
  }

  @Bean
  public RandomGenerator secureRandom() {
    return new SecureRandom();
  }
}
