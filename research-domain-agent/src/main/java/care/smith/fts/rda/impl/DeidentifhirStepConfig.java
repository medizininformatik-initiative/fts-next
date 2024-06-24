package care.smith.fts.rda.impl;

import care.smith.fts.util.HTTPClientConfig;
import java.io.File;
import java.time.Duration;

public record DeidentifhirStepConfig(
    TCAConfig tca, Duration dateShift, File deidentifhirConfigFile) {

  record TCAConfig(HTTPClientConfig server, String domain) {}
}
