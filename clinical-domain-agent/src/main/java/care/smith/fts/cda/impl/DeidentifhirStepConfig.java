package care.smith.fts.cda.impl;

import care.smith.fts.util.HttpClientConfig;
import java.io.File;
import java.time.Duration;

public record DeidentifhirStepConfig(
    TCAConfig tca, Duration maxDateShift, File deidentifhirConfig, File scraperConfig) {

  public record TCAConfig(HttpClientConfig server, String domain) {}
}
