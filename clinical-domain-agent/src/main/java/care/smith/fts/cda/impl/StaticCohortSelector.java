package care.smith.fts.cda.impl;

import care.smith.fts.api.CohortSelector;
import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatient.ConsentedPolicies;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("staticCohortSelector")
public class StaticCohortSelector implements CohortSelector.Factory<StaticCohortSelector.Config> {

  public record Config(List<String> pids) {}

  public StaticCohortSelector() {}

  @Override
  public Class<Config> getConfigType() {
    return Config.class;
  }

  @Override
  public CohortSelector create(CohortSelector.Config ignored, Config config) {
    return () ->
        config.pids().stream()
            .map(id -> new ConsentedPatient(id, new ConsentedPolicies()))
            .toList();
  }
}
