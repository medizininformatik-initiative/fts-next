package care.smith.fts.cda.impl;

import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.tca.TCADomains;
import java.io.File;
import java.time.Duration;

public record DeidentifhirStepConfig(
    TCAConfig trustCenterAgent,
    Duration maxDateShift,
    File deidentifhirConfig,
    File scraperConfig) {

  public record TCAConfig(HttpClientConfig server, TCADomains domains) {}
}
