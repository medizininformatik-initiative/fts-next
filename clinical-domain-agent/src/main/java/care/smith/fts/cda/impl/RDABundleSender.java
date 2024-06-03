package care.smith.fts.cda.impl;

import care.smith.fts.api.BundleSender;
import org.springframework.stereotype.Component;

@Component("rdaBundleSender")
public class RDABundleSender implements BundleSender.Factory<RDABundleSender.Config> {

  public record Config() {}

  @Override
  public Class<Config> getConfigType() {
    return Config.class;
  }

  @Override
  public BundleSender create(BundleSender.Config commonConfig, Config implConfig) {
    // TODO Implement
    throw new UnsupportedOperationException();
  }
}
