package care.smith.fts.cda.impl;

import care.smith.fts.api.cda.DataSelector;
import care.smith.fts.cda.services.FhirResolveService;
import care.smith.fts.util.RetryStrategy;
import care.smith.fts.util.WebClientFactory;
import org.springframework.stereotype.Component;

@Component("everythingDataSelector")
public class EverythingDataSelectorFactory
    implements DataSelector.Factory<EverythingDataSelectorConfig> {

  private final WebClientFactory clientFactory;
  private final RetryStrategy retryStrategy;

  public EverythingDataSelectorFactory(
      WebClientFactory clientFactory, RetryStrategy retryStrategy) {
    this.clientFactory = clientFactory;
    this.retryStrategy = retryStrategy;
  }

  @Override
  public Class<EverythingDataSelectorConfig> getConfigType() {
    return EverythingDataSelectorConfig.class;
  }

  @Override
  public DataSelector create(DataSelector.Config common, EverythingDataSelectorConfig config) {
    var client = clientFactory.create(config.fhirServer());
    var resolver = new FhirResolveService(client, retryStrategy);
    return new EverythingDataSelector(common, client, resolver, retryStrategy, config.pageSize());
  }
}
