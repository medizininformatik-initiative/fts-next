package care.smith.fts.cda.test;

import care.smith.fts.api.cda.CohortSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("mockCohortSelector")
public class MockCohortSelector implements CohortSelector.Factory<MockCohortSelector.Config> {
  @Override
  public Class<Config> getConfigType() {
    return Config.class;
  }

  @Override
  public CohortSelector create(CohortSelector.Config commonConfig, Config implConfig) {
    return pids -> {
      throw new UnsupportedOperationException();
    };
  }

  public record Config(String known) {}
}
