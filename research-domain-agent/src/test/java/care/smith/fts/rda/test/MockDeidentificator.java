package care.smith.fts.rda.test;

import care.smith.fts.api.rda.Deidentificator;
import org.springframework.stereotype.Component;

@Component("mockDeidentificator")
public class MockDeidentificator implements Deidentificator.Factory<MockDeidentificator.Config> {
  @Override
  public Class<Config> getConfigType() {
    return Config.class;
  }

  @Override
  public Deidentificator create(Deidentificator.Config commonConfig, Config implConfig) {
    return (b) -> {
      throw new UnsupportedOperationException();
    };
  }

  public record Config(boolean deidentify) {}
}
