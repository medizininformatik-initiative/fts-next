package care.smith.fts.cda.test;

import care.smith.fts.api.CohortSelector;
import care.smith.fts.api.ConsentedPatient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component("mockCohortSelector")
public class MockCohortSelector implements CohortSelector.Factory<MockCohortSelector.Config> {
  @Override
  public Class<Config> getConfigType() {
    return Config.class;
  }

  @Override
  public CohortSelector create(CohortSelector.Config commonConfig, Config implConfig) {
    return () -> {
      throw new UnsupportedOperationException();
    };
  }

  public record Config(String known) {}
}
