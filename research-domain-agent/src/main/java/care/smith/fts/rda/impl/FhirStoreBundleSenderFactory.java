package care.smith.fts.rda.impl;

import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.util.RetryStrategy;
import care.smith.fts.util.WebClientFactory;
import org.springframework.stereotype.Component;

@Component("fhirStoreBundleSender")
public class FhirStoreBundleSenderFactory
    implements BundleSender.Factory<FhirStoreBundleSenderConfig> {

  private final RetryStrategy retryStrategy;
  private final WebClientFactory clientFactory;

  public FhirStoreBundleSenderFactory(WebClientFactory clientFactory, RetryStrategy retryStrategy) {
    this.clientFactory = clientFactory;
    this.retryStrategy = retryStrategy;
  }

  @Override
  public Class<FhirStoreBundleSenderConfig> getConfigType() {
    return FhirStoreBundleSenderConfig.class;
  }

  @Override
  public BundleSender create(
      BundleSender.Config commonConfig, FhirStoreBundleSenderConfig implConfig) {
    var client = clientFactory.create(implConfig.server());
    return new FhirStoreBundleSender(
        client, retryStrategy, implConfig.server().baseUrl(), implConfig.maxConcurrency());
  }
}
