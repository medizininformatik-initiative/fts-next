package care.smith.fts.tca.deidentification.configuration;

import care.smith.fts.tca.deidentification.FhirShiftedDatesProvider;
import care.smith.fts.util.HTTPClientConfig;
import care.smith.fts.util.auth.HTTPClientAuthMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import redis.clients.jedis.JedisPool;

@Configuration
@ConfigurationProperties(prefix = "de-identification.gpas.fhir")
@Data
public class GpasFhirDeIdentificationConfiguration {
  @NotBlank String baseUrl;
  @NotNull HTTPClientAuthMethod.AuthMethod auth;

  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @Bean("gpasFhirHttpClient")
  public WebClient httpClient() {
    HTTPClientConfig httpClientConfig = new HTTPClientConfig(baseUrl, auth);
    return httpClientConfig.createClient(WebClient.builder());
  }

  @Bean
  public RandomGenerator secureRandom() {
    return new SecureRandom();
  }

  @Bean
  public FhirShiftedDatesProvider fhirShiftedDateProvider(JedisPool jedisPool) {
    return new FhirShiftedDatesProvider(jedisPool);
  }
}
