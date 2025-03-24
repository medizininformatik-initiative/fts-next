package care.smith.fts.tca.consent.configuration;

import static care.smith.fts.tca.consent.GicsFhirUtil.verifyGicsCapabilities;

import care.smith.fts.tca.consent.GicsFhirConsentedPatientsProvider;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.LogUtil;
import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.auth.HttpClientAuth;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
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
@ConfigurationProperties(prefix = "consent.gics.fhir")
@Data
public class GicsFhirConfiguration {

  private static final List<String> GICS_OPERATIONS =
      List.of("allConsentsForDomain", "allConsentsForPerson");

  @NotBlank String baseUrl;
  @Deprecated Integer defaultPageSize;
  HttpClientAuth.Config auth = HttpClientAuth.Config.builder().none("").build();
  HttpClientConfig.Ssl ssl = null;

  @Bean("gicsFhirDefaultPageSize")
  Integer defaultPageSize() {
    if (defaultPageSize != null) {
      log.warn(
          "consent.gics.fhir.defaultPageSize is deprecated. Use consent.gics.pageSize instead.");
    }
    return defaultPageSize;
  }

  @Bean("gicsFhirHttpClient")
  public WebClient gicsClient(WebClientFactory clientFactory) {
    var config = new HttpClientConfig(baseUrl, auth, ssl);
    return clientFactory.create(config);
  }

  @Bean("gicsApplicationRunner")
  ApplicationRunner runner(@Qualifier("gicsFhirHttpClient") WebClient gicsClient) {
    return args ->
        verifyGicsCapabilities(gicsClient)
            .doOnNext(c -> log.info("gICS {} available", c.getSoftware().getVersion()))
            .doOnError(GicsFhirConfiguration::logWarning)
            .onErrorComplete()
            .subscribe();
  }

  private static void logWarning(Throwable e) {
    var msg =
        """
        Connection to gICS could not be established on agent startup. \
        The agent will continue startup anyway, in case gICS connection will be \
        available later on.""";
    LogUtil.warnWithDebugException(log, msg, e);
  }

  @Bean
  public GicsFhirConsentedPatientsProvider fhirConsentedPatientsProvider(
      @Qualifier("gicsFhirHttpClient") WebClient gicsClient, MeterRegistry meterRegistry) {
    return new GicsFhirConsentedPatientsProvider(gicsClient, meterRegistry);
  }
}
