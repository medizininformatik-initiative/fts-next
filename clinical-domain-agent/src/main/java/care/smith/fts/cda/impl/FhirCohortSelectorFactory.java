package care.smith.fts.cda.impl;

import care.smith.fts.api.cda.CohortSelector;
import care.smith.fts.cda.TransferProcessRunnerConfig;
import care.smith.fts.util.RetryStrategy;
import care.smith.fts.util.WebClientFactory;
import org.springframework.stereotype.Component;

@Component("fhirCohortSelector")
public class FhirCohortSelectorFactory implements CohortSelector.Factory<FhirCohortSelectorConfig> {

  private final WebClientFactory clientFactory;
  private final RetryStrategy retryStrategy;
  private final int cohortSelectionConcurrency;

  public FhirCohortSelectorFactory(
      WebClientFactory clientFactory,
      RetryStrategy retryStrategy,
      TransferProcessRunnerConfig runnerConfig) {
    this.clientFactory = clientFactory;
    this.retryStrategy = retryStrategy;
    this.cohortSelectionConcurrency = runnerConfig.cohortSelectionConcurrency();
  }

  @Override
  public Class<FhirCohortSelectorConfig> getConfigType() {
    return FhirCohortSelectorConfig.class;
  }

  @Override
  public CohortSelector create(CohortSelector.Config ignored, FhirCohortSelectorConfig config) {
    var client = clientFactory.create(config.server());
    return new FhirCohortSelector(config, client, retryStrategy, cohortSelectionConcurrency);
  }
}
