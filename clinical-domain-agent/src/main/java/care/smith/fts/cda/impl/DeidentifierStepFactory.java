package care.smith.fts.cda.impl;

import static care.smith.fts.cda.services.deidentifier.DeidentifierUtils.requireValidProfile;
import static com.typesafe.config.ConfigFactory.parseFile;
import static java.util.Objects.requireNonNull;

import care.smith.fts.api.cda.Deidentificator;
import care.smith.fts.util.RetryStrategy;
import care.smith.fts.util.WebClientFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component("deidentifierDeidentificator")
public class DeidentifierStepFactory implements Deidentificator.Factory<DeidentifierStepConfig> {

  private final WebClientFactory clientFactory;
  private final MeterRegistry meterRegistry;
  private final RetryStrategy retryStrategy;

  public DeidentifierStepFactory(
      WebClientFactory clientFactory, MeterRegistry meterRegistry, RetryStrategy retryStrategy) {
    this.clientFactory = clientFactory;
    this.meterRegistry = meterRegistry;
    this.retryStrategy = retryStrategy;
  }

  @Override
  public Class<DeidentifierStepConfig> getConfigType() {
    return DeidentifierStepConfig.class;
  }

  @Override
  public Deidentificator create(
      Deidentificator.Config commonConfig, DeidentifierStepConfig implConfig) {
    var httpClient = clientFactory.create(implConfig.trustCenterAgent().server());
    var config = parseFile(requireNonNull(implConfig.deidentifierConfig()));
    requireValidProfile(config);

    return new DeidentifierStep(
        httpClient,
        implConfig.trustCenterAgent().domains(),
        implConfig.maxDateShift(),
        implConfig.dateShiftPreserve(),
        config,
        meterRegistry,
        retryStrategy);
  }
}
