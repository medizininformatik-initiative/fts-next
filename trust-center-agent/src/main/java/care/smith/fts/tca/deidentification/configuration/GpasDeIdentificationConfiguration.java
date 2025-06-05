package care.smith.fts.tca.deidentification.configuration;

import static care.smith.fts.util.fhir.FhirClientUtils.fetchCapabilityStatementOperations;
import static care.smith.fts.util.fhir.FhirClientUtils.requireOperations;

import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.LogUtil;
import care.smith.fts.util.WebClientFactory;
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

@Slf4j
@Configuration
@ConfigurationProperties(prefix = "de-identification.gpas")
@Data
public class GpasDeIdentificationConfiguration {

  public static final List<String> GPAS_OPERATIONS = List.of("pseudonymizeAllowCreate");
  private HttpClientConfig fhir;

  @Bean("gpasFhirHttpClient")
  public WebClient gpasClient(WebClientFactory clientFactory) {
    return clientFactory.create(fhir);
  }

  @Bean("gpasApplicationRunner")
  ApplicationRunner runner(@Qualifier("gpasFhirHttpClient") WebClient gpasClient) {
    return args ->
        fetchCapabilityStatementOperations(gpasClient)
            .flatMap(c -> requireOperations(c, GPAS_OPERATIONS))
            .doOnNext(i -> log.info("gPAS available"))
            .doOnError(GpasDeIdentificationConfiguration::logWarning)
            .onErrorComplete()
            .block();
  }

  private static void logWarning(Throwable e) {
    var msg =
        """
        Connection to gPAS could not be established on agent startup. \
        The agent will continue startup anyway, in case gPAS connection will be \
        available later on.\
        """;
    LogUtil.warnWithDebugException(log, msg, e);
  }

  @Bean
  public RandomGenerator secureRandom() {
    return new SecureRandom();
  }
}
