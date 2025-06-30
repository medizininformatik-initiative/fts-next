package care.smith.fts.cda.impl;

import static reactor.core.publisher.Flux.fromStream;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.cda.CohortSelector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("externalCohortSelector")
public class ExternalCohortSelector
    implements CohortSelector.Factory<ExternalCohortSelector.Config> {

  public record Config(String patientIdentifierSystem) {}

  public ExternalCohortSelector() {}

  @Override
  public Class<Config> getConfigType() {
    return Config.class;
  }

  @Override
  public CohortSelector create(CohortSelector.Config ignored, Config config) {
    return pids -> {
      log.debug("pids: {}", pids);
      return fromStream(
          pids.stream().map(id -> new ConsentedPatient(id, config.patientIdentifierSystem())));
    };
  }
}
