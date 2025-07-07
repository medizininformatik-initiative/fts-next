package care.smith.fts.cda.impl;

import care.smith.fts.api.cda.CohortSelector;
import care.smith.fts.util.WebClientFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component("trustCenterAgentCohortSelector")
public class TcaCohortSelectorFactory implements CohortSelector.Factory<TcaCohortSelectorConfig> {

  private final WebClientFactory clientFactory;
  private final MeterRegistry meterRegistry;

  public TcaCohortSelectorFactory(WebClientFactory clientFactory, MeterRegistry meterRegistry) {
    this.clientFactory = clientFactory;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Class<TcaCohortSelectorConfig> getConfigType() {
    return TcaCohortSelectorConfig.class;
  }

  @Override
  public CohortSelector create(CohortSelector.Config ignored, TcaCohortSelectorConfig config) {
    var client = clientFactory.create(config.server());
    return new TcaCohortSelector(config, client, meterRegistry);
  }
}
