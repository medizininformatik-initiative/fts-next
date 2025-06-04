package care.smith.fts.tca.consent.configuration;

import static care.smith.fts.tca.consent.GicsFhirUtil.verifyGicsCapabilities;
import static java.util.Objects.requireNonNullElse;
import static lombok.AccessLevel.PACKAGE;

import care.smith.fts.tca.consent.GicsConfigured;
import care.smith.fts.tca.consent.GicsFhirConsentedPatientsProvider;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.LogUtil;
import care.smith.fts.util.WebClientFactory;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Conditional(GicsConfigured.class)
@Configuration
@ConfigurationProperties(prefix = "consent.gics")
@Setter(PACKAGE)
public class GicsConfiguration {
  private static final int DEFAULT_PAGE_SIZE = 50;

  private Integer pageSize = null;
  private HttpClientConfig fhir;

  @Bean("gicsPageSize")
  int pageSize() {
    return requireNonNullElse(pageSize, DEFAULT_PAGE_SIZE);
  }

  @Bean("gicsFhirHttpClient")
  public WebClient gicsClient(WebClientFactory clientFactory) {
    return clientFactory.create(fhir);
  }

  @Bean("gicsApplicationRunner")
  ApplicationRunner runner(@Qualifier("gicsFhirHttpClient") WebClient gicsClient) {
    return args ->
        verifyGicsCapabilities(gicsClient)
            .doOnNext(c -> log.info("gICS {} available", c.getSoftware().getVersion()))
            .doOnError(GicsConfiguration::logWarning)
            .onErrorComplete()
            .subscribe();
  }

  private static void logWarning(Throwable e) {
    var msg =
        """
        Connection to gICS could not be established on agent startup. \
        The agent will continue startup anyway, in case gICS connection will be \
        available later on.\
        """;
    LogUtil.warnWithDebugException(log, msg, e);
  }

  @Bean
  public GicsFhirConsentedPatientsProvider fhirConsentedPatientsProvider(
      @Qualifier("gicsFhirHttpClient") WebClient gicsClient, MeterRegistry meterRegistry) {
    return new GicsFhirConsentedPatientsProvider(gicsClient, meterRegistry);
  }
}
