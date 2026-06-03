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
    return identifiers -> {
      log.debug("identifiers: {}", identifiers);
      var msg = "patientIdentifierSystem={}, emitting {} patients";
      log.trace(msg, config.patientIdentifierSystem(), identifiers.size());
      return fromStream(
          identifiers.stream()
              .map(
                  identifier ->
                      new ConsentedPatient(identifier, config.patientIdentifierSystem())));
    };
  }
}
