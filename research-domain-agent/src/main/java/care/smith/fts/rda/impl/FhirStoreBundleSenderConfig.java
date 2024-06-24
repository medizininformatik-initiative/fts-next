package care.smith.fts.rda.impl;

import care.smith.fts.util.HTTPClientConfig;
import jakarta.validation.constraints.NotNull;

public record FhirStoreBundleSenderConfig(
    /* */
    @NotNull HTTPClientConfig fhirServer,

    /* */
    String project) {}
