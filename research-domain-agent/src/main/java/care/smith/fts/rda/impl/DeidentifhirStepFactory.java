package care.smith.fts.rda.impl;

import static java.util.Objects.requireNonNull;

import care.smith.fts.api.rda.Deidentificator;
import care.smith.fts.util.WebClientFactory;
import com.typesafe.config.ConfigFactory;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Deprecated(forRemoval = true)
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
    log.warn(
        "'deidentifhir' deidentificator is deprecated, use 'idMapper' instead. The"
            + " 'deidentifhirConfig' and 'dateShift' fields will be removed in a future release.");
    var httpClient = clientFactory.create(implConfig.trustCenterAgent().server());
    var config = ConfigFactory.parseFile(requireNonNull(implConfig.deidentifhirConfig()));
    return new DeidentifhirStep(config, httpClient, meterRegistry);
  }
}
