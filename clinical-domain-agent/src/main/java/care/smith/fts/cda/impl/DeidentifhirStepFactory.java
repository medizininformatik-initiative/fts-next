package care.smith.fts.cda.impl;

import static com.typesafe.config.ConfigFactory.parseFile;
import static java.util.Objects.requireNonNull;

import care.smith.fts.api.cda.Deidentificator;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientSsl;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component("deidentifhirDeidentificator")
public class DeidentifhirStepFactory implements Deidentificator.Factory<DeidentifhirStepConfig> {

  private final WebClient.Builder builder;
  private final WebClientSsl ssl;
  private final MeterRegistry meterRegistry;

  public DeidentifhirStepFactory(
      WebClient.Builder builder, WebClientSsl ssl, MeterRegistry meterRegistry) {
    this.builder = builder;
    this.ssl = ssl;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Class<DeidentifhirStepConfig> getConfigType() {
    return DeidentifhirStepConfig.class;
  }

  @Override
  public Deidentificator create(
      Deidentificator.Config commonConfig, DeidentifhirStepConfig implConfig) {
    var httpClient = implConfig.trustCenterAgent().server().createClient(builder, ssl);

    return new DeidentifhirStep(
        httpClient,
        implConfig.trustCenterAgent().domains(),
        implConfig.maxDateShift(),
        parseFile(requireNonNull(implConfig.deidentifhirConfig())),
        parseFile(requireNonNull(implConfig.scraperConfig())),
        meterRegistry);
  }
}
