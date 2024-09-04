package care.smith.fts.cda.impl;

import care.smith.fts.api.cda.BundleSender;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component("researchDomainAgentBundleSender")
public class RDABundleSenderFactory implements BundleSender.Factory<RDABundleSenderConfig> {

  private final WebClient.Builder builder;
  private final MeterRegistry meterRegistry;

  public RDABundleSenderFactory(WebClient.Builder builder, @Autowired MeterRegistry meterRegistry) {
    this.builder = builder;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Class<RDABundleSenderConfig> getConfigType() {
    return RDABundleSenderConfig.class;
  }

  @Override
  public BundleSender create(BundleSender.Config commonConfig, RDABundleSenderConfig implConfig) {
    return new RDABundleSender(
        implConfig, implConfig.server().createClient(builder), meterRegistry);
  }
}
