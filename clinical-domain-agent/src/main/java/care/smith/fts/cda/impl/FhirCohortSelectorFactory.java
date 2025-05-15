package care.smith.fts.cda.impl;

import care.smith.fts.api.cda.CohortSelector;
import care.smith.fts.util.WebClientFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component("fhirCohortSelector")
public class FhirCohortSelectorFactory implements CohortSelector.Factory<FhirCohortSelectorConfig> {

  private final WebClientFactory clientFactory;
  private final MeterRegistry meterRegistry;

  public FhirCohortSelectorFactory(WebClientFactory clientFactory, MeterRegistry meterRegistry) {
    this.clientFactory = clientFactory;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Class<FhirCohortSelectorConfig> getConfigType() {
    return FhirCohortSelectorConfig.class;
  }

  @Override
  public CohortSelector create(CohortSelector.Config ignored, FhirCohortSelectorConfig config) {
    var client = clientFactory.create(config.server());
    return new FhirCohortSelector(config, client, meterRegistry);
  }
}
