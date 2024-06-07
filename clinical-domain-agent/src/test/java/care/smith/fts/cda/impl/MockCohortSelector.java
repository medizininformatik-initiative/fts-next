package care.smith.fts.cda.impl;

import care.smith.fts.api.CohortSelector;
import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatient.ConsentedPolicies;
import java.util.List;
import org.springframework.stereotype.Component;

@Component("mockCohortSelector")
public class MockCohortSelector implements CohortSelector.Factory<MockCohortSelector.Config> {
  @Override
  public Class<Config> getConfigType() {
    return Config.class;
  }

  @Override
  public CohortSelector create(CohortSelector.Config commonConfig, Config implConfig) {
    return new Impl(implConfig);
  }

  public record Config(List<String> pids) {}

  public static class Impl implements CohortSelector {
    private final MockCohortSelector.Config implConfig;

    public Impl(MockCohortSelector.Config implConfig) {
      this.implConfig = implConfig;
    }

    @Override
    public List<ConsentedPatient> selectCohort() {
      return implConfig.pids().stream()
          .map(pid -> new ConsentedPatient(pid, new ConsentedPolicies()))
          .toList();
    }
  }
}
