package care.smith.fts.cda.impl;

import care.smith.fts.api.cda.CohortSelector;
import care.smith.fts.util.WebClientFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component("trustCenterAgentCohortSelector")
public class TCACohortSelectorFactory implements CohortSelector.Factory<TCACohortSelectorConfig> {

  private final WebClientFactory clientFactory;
  private final MeterRegistry meterRegistry;

  public TCACohortSelectorFactory(WebClientFactory clientFactory, MeterRegistry meterRegistry) {
    this.clientFactory = clientFactory;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Class<TCACohortSelectorConfig> getConfigType() {
    return TCACohortSelectorConfig.class;
  }

  @Override
  public CohortSelector create(CohortSelector.Config ignored, TCACohortSelectorConfig config) {
    var client = clientFactory.create(config.server());
    return new TCACohortSelector(config, client, meterRegistry);
  }
}
