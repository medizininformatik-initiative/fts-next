package care.smith.fts.cda.impl;

import care.smith.fts.api.cda.BundleSender;
import care.smith.fts.util.WebClientFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component("researchDomainAgentBundleSender")
public class RdaBundleSenderFactory implements BundleSender.Factory<RdaBundleSenderConfig> {

  private final WebClientFactory clientFactory;
  private final MeterRegistry meterRegistry;

  public RdaBundleSenderFactory(WebClientFactory clientFactory, MeterRegistry meterRegistry) {
    this.clientFactory = clientFactory;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Class<RdaBundleSenderConfig> getConfigType() {
    return RdaBundleSenderConfig.class;
  }

  @Override
  public BundleSender create(BundleSender.Config commonConfig, RdaBundleSenderConfig implConfig) {
    return new RdaBundleSender(
        implConfig, clientFactory.create(implConfig.server()), meterRegistry);
  }
}
