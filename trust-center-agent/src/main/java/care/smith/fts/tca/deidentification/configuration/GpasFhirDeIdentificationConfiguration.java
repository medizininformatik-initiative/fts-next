package care.smith.fts.tca.deidentification.configuration;

import care.smith.fts.tca.deidentification.FhirPseudonymProvider;
import care.smith.fts.tca.deidentification.FhirShiftedDatesProvider;
import care.smith.fts.util.HTTPClientConfig;
import care.smith.fts.util.auth.HTTPClientAuthMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
  public CloseableHttpClient httpClient() {
    HTTPClientConfig httpClientConfig = new HTTPClientConfig(baseUrl, auth);
    return httpClientConfig.createClient(HttpClientBuilder.create());
  }

  @Bean
  public FhirPseudonymProvider fhirPseudonymProvider(
      CloseableHttpClient httpClient,
      ObjectMapper objectMapper,
      JedisPool jedisPool,
      PseudonymizationConfiguration pseudonymizationConfiguration) {
    return new FhirPseudonymProvider(
        httpClient, objectMapper, jedisPool, pseudonymizationConfiguration);
  }

  @Bean
  public FhirShiftedDatesProvider fhirShiftedDateProvider(JedisPool jedisPool) {
    return new FhirShiftedDatesProvider(jedisPool);
  }
}
