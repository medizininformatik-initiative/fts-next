package care.smith.fts.cda.impl;

import care.smith.fts.api.cda.BundleSender;
import care.smith.fts.util.WebClientFactory;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component("researchDomainAgentBundleSender")
public class RDABundleSenderFactory implements BundleSender.Factory<RDABundleSenderConfig> {

  private final WebClientFactory clientFactory;
  private final MeterRegistry meterRegistry;

  public RDABundleSenderFactory(WebClientFactory clientFactory, MeterRegistry meterRegistry) {
    this.clientFactory = clientFactory;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Class<RDABundleSenderConfig> getConfigType() {
    return RDABundleSenderConfig.class;
  }

  @Override
  public BundleSender create(BundleSender.Config commonConfig, RDABundleSenderConfig implConfig) {
    return new RDABundleSender(
        implConfig, clientFactory.create(implConfig.server()), meterRegistry);
  }
}
