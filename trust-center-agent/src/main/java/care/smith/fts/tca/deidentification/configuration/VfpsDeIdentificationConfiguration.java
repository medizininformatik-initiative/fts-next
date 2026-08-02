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
@ConfigurationProperties(prefix = "de-identification.vfps")
@Data
public class VfpsDeIdentificationConfiguration {

  public static final List<String> VFPS_OPERATIONS = List.of("create-pseudonym");

  private HttpClientConfig fhir;

  /** Maximum number of concurrent requests to Vfps for batch processing. */
  private int concurrency = 4;

  @Bean("vfpsFhirHttpClient")
  @ConditionalOnProperty(name = "de-identification.backend.type", havingValue = "vfps")
  public WebClient vfpsClient(WebClientFactory clientFactory) {
    return clientFactory.create(fhir);
  }

  @Bean("vfpsApplicationRunner")
  @ConditionalOnProperty(name = "de-identification.backend.type", havingValue = "vfps")
  ApplicationRunner runner(@Qualifier("vfpsFhirHttpClient") WebClient vfpsClient) {
    return args ->
        fetchCapabilityStatementOperations(vfpsClient)
            .flatMap(c -> requireOperations(c, VFPS_OPERATIONS))
            .doOnNext(i -> log.info("Vfps available"))
            .doOnError(VfpsDeIdentificationConfiguration::logWarning)
            .onErrorComplete()
            .block();
  }

  private static void logWarning(Throwable e) {
    var msg =
        """
        Connection to Vfps could not be established on agent startup. \
        The agent will continue startup anyway, in case Vfps connection will be \
        available later on.\
        """;
    LogUtil.warnWithDebugException(log, msg, e);
  }
}
