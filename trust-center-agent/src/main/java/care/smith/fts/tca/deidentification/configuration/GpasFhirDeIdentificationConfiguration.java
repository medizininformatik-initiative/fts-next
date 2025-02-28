package care.smith.fts.tca.deidentification.configuration;

import static care.smith.fts.util.FhirClientUtils.fetchCapabilityStatement;
import static care.smith.fts.util.FhirClientUtils.verifyOperationsExist;

import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.auth.HttpClientAuth;
import care.smith.fts.util.error.fhir.FhirConnectException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.constraints.NotBlank;
import java.security.SecureRandom;
import java.util.List;
import java.util.random.RandomGenerator;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Configuration
@ConfigurationProperties(prefix = "de-identification.gpas.fhir")
@Data
public class GpasFhirDeIdentificationConfiguration {
  @NotBlank String baseUrl;
  HttpClientAuth.Config auth = HttpClientAuth.Config.builder().none("").build();

  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }

  @Bean("gpasFhirHttpClient")
  public WebClient gpasClient(WebClientFactory clientFactory) {
    return clientFactory.create(new HttpClientConfig(baseUrl, auth));
  }

  @Bean("gpasApplicationRunner")
  ApplicationRunner runner(@Qualifier("gpasFhirHttpClient") WebClient gpasClient) {
    return args ->
        fetchCapabilityStatement(gpasClient)
            .flatMap(
                c ->
                    verifyOperationsExist(c, List.of("pseudonymizeAllowCreate"))
                        ? Mono.just(c)
                        : Mono.error(new FhirConnectException("Server is missing capabilities")))
            .doOnNext(i -> log.info("gPAS available"))
            .doOnError(
                e -> {
                  log.warn(
                      "Connection to gPAS could not be established on agent startup. The agent will continue startup anyway, in case gPAS connection will be available later on.");
                  log.debug("", e);
                })
            .onErrorComplete()
            .block();
  }

  @Bean
  public RandomGenerator secureRandom() {
    return new SecureRandom();
  }
}
