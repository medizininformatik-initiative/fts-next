package care.smith.fts.cda.test;

import care.smith.fts.api.cda.Deidentificator;
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
