package care.smith.fts.rda.impl;

import care.smith.fts.util.HttpClientConfig;
import jakarta.validation.constraints.NotNull;

public record FhirStoreBundleSenderConfig(
    /* */
    @NotNull HttpClientConfig server,

    /* */
    String project) {}
