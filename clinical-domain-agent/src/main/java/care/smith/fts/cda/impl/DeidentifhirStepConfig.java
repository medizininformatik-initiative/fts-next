package care.smith.fts.cda.impl;

import care.smith.fts.util.HTTPClientConfig;
import java.io.File;
import java.time.Duration;

public record DeidentifhirStepConfig(
    TCAConfig tca, Duration dateShift, File deidentifhirConfig, File scraperConfig) {

  public record TCAConfig(HTTPClientConfig server, String domain) {}
}
