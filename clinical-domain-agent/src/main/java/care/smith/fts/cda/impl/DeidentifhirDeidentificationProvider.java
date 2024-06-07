package care.smith.fts.cda.impl;

import care.smith.fts.api.DeidentificationProvider;
import org.springframework.stereotype.Component;

@Component("deidentifhirDeidentificationProvider")
public class DeidentifhirDeidentificationProvider
    implements DeidentificationProvider.Factory<DeidentifhirDeidentificationProvider.Config> {

  public record Config() {}

  @Override
  public Class<Config> getConfigType() {
    return Config.class;
  }

  @Override
  public DeidentificationProvider create(
      DeidentificationProvider.Config commonConfig, Config implConfig) {
    // TODO Implement
    return b -> {
      throw new UnsupportedOperationException();
    };
  }
}