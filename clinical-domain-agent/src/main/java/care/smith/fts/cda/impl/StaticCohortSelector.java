package care.smith.fts.cda.impl;

import static reactor.core.publisher.Flux.fromStream;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.cda.CohortSelector;
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
    return () -> fromStream(config.pids().stream().map(StaticCohortSelector::staticPatient));
  }

  private static ConsentedPatient staticPatient(String id) {
    return new ConsentedPatient(id);
  }
}
