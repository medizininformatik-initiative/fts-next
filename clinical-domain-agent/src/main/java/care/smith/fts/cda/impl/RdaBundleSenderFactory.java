package care.smith.fts.cda.impl;

import care.smith.fts.api.cda.BundleSender;
import care.smith.fts.util.RetryStrategy;
import care.smith.fts.util.WebClientFactory;
import org.springframework.stereotype.Component;

@Component("researchDomainAgentBundleSender")
public class RdaBundleSenderFactory implements BundleSender.Factory<RdaBundleSenderConfig> {

  private final WebClientFactory clientFactory;
  private final RetryStrategy retryStrategy;

  public RdaBundleSenderFactory(WebClientFactory clientFactory, RetryStrategy retryStrategy) {
    this.clientFactory = clientFactory;
    this.retryStrategy = retryStrategy;
  }

  @Override
  public Class<RdaBundleSenderConfig> getConfigType() {
    return RdaBundleSenderConfig.class;
  }

  @Override
  public BundleSender create(BundleSender.Config commonConfig, RdaBundleSenderConfig implConfig) {
    return new RdaBundleSender(
        implConfig, clientFactory.create(implConfig.server()), retryStrategy);
  }
}
