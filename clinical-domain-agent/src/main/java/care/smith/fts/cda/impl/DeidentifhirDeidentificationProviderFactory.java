package care.smith.fts.cda.impl;

import care.smith.fts.api.DeidentificationProvider;
import org.springframework.stereotype.Component;

@Component("deidentifhirDeidentificationProvider")
public class DeidentifhirDeidentificationProviderFactory
    implements DeidentificationProvider.Factory<
        DeidentifhirDeidentificationProviderFactory.Config> {

  public record Config() {}

  @Override
  public Class<Config> getConfigType() {
    return Config.class;
  }

  @Override
  public DeidentificationProvider create(
      DeidentificationProvider.Config commonConfig, Config implConfig) {
    return new DeidentifhirDeidentificationProvider();
  }
}
