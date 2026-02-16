package care.smith.fts.rda.impl;

import static java.util.Objects.requireNonNull;

import care.smith.fts.api.rda.Deidentificator;
import care.smith.fts.util.WebClientFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component("idMapperDeidentificator")
public class IdMapperStepFactory implements Deidentificator.Factory<IdMapperStepConfig> {

  private final WebClientFactory clientFactory;
  private final MeterRegistry meterRegistry;

  public IdMapperStepFactory(WebClientFactory clientFactory, MeterRegistry meterRegistry) {
    this.clientFactory = clientFactory;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Class<IdMapperStepConfig> getConfigType() {
    return IdMapperStepConfig.class;
  }

  @Override
  public Deidentificator create(
      Deidentificator.Config commonConfig, IdMapperStepConfig implConfig) {
    var tcaConfig =
        requireNonNull(implConfig.trustCenterAgent(), "trustCenterAgent config is required");
    var httpClient =
        clientFactory.create(
            requireNonNull(tcaConfig.server(), "trustCenterAgent.server config is required"));
    return new IdMapperStep(httpClient, meterRegistry);
  }
}
