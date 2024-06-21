package care.smith.fts.rda.impl;

import care.smith.fts.util.auth.HTTPClientAuthMethod;
import java.io.File;
import java.time.Duration;

public record DeidentifhirStepConfig(
    String tcaBaseUrl,
    HTTPClientAuthMethod.AuthMethod auth,
    String domain,
    Duration dateShift,
    File deidentifhirConfigFile,
    File scraperConfigFile) {}
