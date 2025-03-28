package care.smith.fts.rda.impl;

import static java.util.Objects.requireNonNull;

import care.smith.fts.api.rda.Deidentificator;
import care.smith.fts.util.WebClientFactory;
import com.typesafe.config.ConfigFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component("deidentifhirDeidentificator")
public class DeidentifhirStepFactory implements Deidentificator.Factory<DeidentifhirStepConfig> {

  private final WebClientFactory clientFactory;
  private final MeterRegistry meterRegistry;

  public DeidentifhirStepFactory(WebClientFactory clientFactory, MeterRegistry meterRegistry) {
    this.clientFactory = clientFactory;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Class<DeidentifhirStepConfig> getConfigType() {
    return DeidentifhirStepConfig.class;
  }

  @Override
  public Deidentificator create(
      Deidentificator.Config commonConfig, DeidentifhirStepConfig implConfig) {

    var httpClient = clientFactory.create(implConfig.trustCenterAgent().server());
    var config = ConfigFactory.parseFile(requireNonNull(implConfig.deidentifhirConfig()));
    return new DeidentifhirStep(config, httpClient, meterRegistry);
  }
}
