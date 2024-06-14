package care.smith.fts.cda.test;

import care.smith.fts.api.cda.DeidentificationProvider;
import org.springframework.stereotype.Component;

@Component("mockDeidentificationProvider")
public class MockDeidentificationProvider
    implements DeidentificationProvider.Factory<MockDeidentificationProvider.Config> {
  @Override
  public Class<Config> getConfigType() {
    return Config.class;
  }

  @Override
  public DeidentificationProvider create(
      DeidentificationProvider.Config commonConfig, Config implConfig) {
    return (b) -> {
      throw new UnsupportedOperationException();
    };
  }

  public record Config(boolean deidentify) {}
}
