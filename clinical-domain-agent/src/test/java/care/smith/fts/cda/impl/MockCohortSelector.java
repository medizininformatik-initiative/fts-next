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
  public CohortSelector create(
      CohortSelector.Config commonConfig, Config implConfig) {
    return () ->
        implConfig.pids().stream()
            .map(pid -> new ConsentedPatient(pid, new ConsentedPolicies()))
            .toList();
  }

  public record Config(List<String> pids) {}
}
