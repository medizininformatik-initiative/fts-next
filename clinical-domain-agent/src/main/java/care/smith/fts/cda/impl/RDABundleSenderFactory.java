package care.smith.fts.cda.impl;

import care.smith.fts.api.cda.BundleSender;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component("researchDomainAgentBundleSender")
public class RDABundleSenderFactory implements BundleSender.Factory<RDABundleSenderConfig> {

  private final WebClient.Builder builder;

  public RDABundleSenderFactory(WebClient.Builder builder) {
    this.builder = builder;
  }

  @Override
  public Class<RDABundleSenderConfig> getConfigType() {
    return RDABundleSenderConfig.class;
  }

  @Override
  public BundleSender create(BundleSender.Config commonConfig, RDABundleSenderConfig implConfig) {
    return new RDABundleSender(implConfig, implConfig.server().createClient(builder));
  }
}
