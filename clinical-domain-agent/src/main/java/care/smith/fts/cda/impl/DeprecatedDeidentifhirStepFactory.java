package care.smith.fts.cda.impl;

import care.smith.fts.api.cda.Deidentificator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Accepts the old step key {@code deidentifhir} and creates a {@link DeidentifierStep}.
 *
 * @deprecated use the step key {@code deidentifier}
 */
@Slf4j
@Deprecated(forRemoval = true)
@Component("deidentifhirDeidentificator")
public class DeprecatedDeidentifhirStepFactory
    implements Deidentificator.Factory<DeidentifierStepConfig> {

  private final DeidentifierStepFactory factory;

  public DeprecatedDeidentifhirStepFactory(DeidentifierStepFactory factory) {
    this.factory = factory;
  }

  @Override
  public Class<DeidentifierStepConfig> getConfigType() {
    return factory.getConfigType();
  }

  @Override
  public Deidentificator create(
      Deidentificator.Config commonConfig, DeidentifierStepConfig implConfig) {
    log.warn("The deidentificator key 'deidentifhir' is deprecated, use 'deidentifier' instead.");
    return factory.create(commonConfig, implConfig);
  }
}
