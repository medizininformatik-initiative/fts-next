package care.smith.fts.tca.deidentification.configuration;

import static care.smith.fts.util.fhir.FhirClientUtils.fetchCapabilityStatementOperations;
import static care.smith.fts.util.fhir.FhirClientUtils.requireOperations;

import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.LogUtil;
import care.smith.fts.util.WebClientFactory;
import java.util.List;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Configuration
@ConfigurationProperties(prefix = "de-identification.entici")
@Data
public class EnticiDeIdentificationConfiguration {

  public static final List<String> ENTICI_OPERATIONS = List.of("pseudonymize");

  private HttpClientConfig fhir;

  /** Maximum number of concurrent requests to Entici for batch processing. */
  private int concurrency = 4;

  /** Resource type to use in pseudonymization requests (default: Patient). */
  private String resourceType = "Patient";

  /** Optional project name for Entici requests. */
  private String project;

  @Bean("enticiFhirHttpClient")
  @ConditionalOnProperty(name = "de-identification.backend.type", havingValue = "entici")
  public WebClient enticiClient(WebClientFactory clientFactory) {
    return clientFactory.create(fhir);
  }

  @Bean("enticiApplicationRunner")
  @ConditionalOnProperty(name = "de-identification.backend.type", havingValue = "entici")
  ApplicationRunner runner(@Qualifier("enticiFhirHttpClient") WebClient enticiClient) {
    return args ->
        fetchCapabilityStatementOperations(enticiClient)
            .flatMap(c -> requireOperations(c, ENTICI_OPERATIONS))
            .doOnNext(i -> log.info("Entici available"))
            .doOnError(EnticiDeIdentificationConfiguration::logWarning)
            .onErrorComplete()
            .block();
  }

  private static void logWarning(Throwable e) {
    var msg =
        """
        Connection to Entici could not be established on agent startup. \
        The agent will continue startup anyway, in case Entici connection will be \
        available later on.\
        """;
    LogUtil.warnWithDebugException(log, msg, e);
  }
}
