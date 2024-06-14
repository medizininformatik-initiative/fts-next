package care.smith.fts.cda.test;

import care.smith.fts.api.DataSelector;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Component;

@Component("mockDataSelector")
public class MockDataSelector implements DataSelector.Factory<Bundle, MockDataSelector.Config> {
  @Override
  public Class<Config> getConfigType() {
    return Config.class;
  }

  @Override
  public DataSelector<Bundle> create(DataSelector.Config commonConfig, Config implConfig) {
    return a -> {
      throw new UnsupportedOperationException();
    };
  }

  public record Config() {}
}
