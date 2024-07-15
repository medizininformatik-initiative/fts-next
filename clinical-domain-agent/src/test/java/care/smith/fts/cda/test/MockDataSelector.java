package care.smith.fts.cda.test;

import care.smith.fts.api.cda.DataSelector;
import org.springframework.stereotype.Component;

@Component("mockDataSelector")
public class MockDataSelector implements DataSelector.Factory<MockDataSelector.Config> {
  @Override
  public Class<Config> getConfigType() {
    return Config.class;
  }

  @Override
  public DataSelector create(DataSelector.Config commonConfig, Config implConfig) {
    return a -> {
      throw new UnsupportedOperationException();
    };
  }

  public record Config() {}
}
