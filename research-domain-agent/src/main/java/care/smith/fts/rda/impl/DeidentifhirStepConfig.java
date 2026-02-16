package care.smith.fts.rda.impl;

import care.smith.fts.util.HttpClientConfig;
import java.io.File;
import java.time.Duration;

@Deprecated(forRemoval = true)
public record DeidentifhirStepConfig(
    TCAConfig trustCenterAgent, Duration dateShift, File deidentifhirConfig) {

  record TCAConfig(HttpClientConfig server) {}
}
