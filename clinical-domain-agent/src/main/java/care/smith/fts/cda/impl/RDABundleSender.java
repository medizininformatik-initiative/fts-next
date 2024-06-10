package care.smith.fts.cda.impl;

import care.smith.fts.api.BundleSender;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Component;

@Component("researchDomainAgentBundleSender")
public class RDABundleSender implements BundleSender.Factory<Bundle, RDABundleSender.Config> {

  public record Config() {}

  @Override
  public Class<Config> getConfigType() {
    return Config.class;
  }

  @Override
  public BundleSender<Bundle> create(BundleSender.Config commonConfig, Config implConfig) {
    // TODO Implement
    return b -> {
      throw new UnsupportedOperationException();
    };
  }
}
