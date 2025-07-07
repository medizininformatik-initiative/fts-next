package care.smith.fts.cda.impl;

import care.smith.fts.util.HttpClientConfig;
import jakarta.validation.constraints.NotNull;

public record RdaBundleSenderConfig(
    /* */
    @NotNull HttpClientConfig server,

    /* */
    String project) {}
