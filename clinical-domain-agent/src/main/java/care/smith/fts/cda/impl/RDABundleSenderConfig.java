package care.smith.fts.cda.impl;

import care.smith.fts.util.HttpClientConfig;
import jakarta.validation.constraints.NotNull;

public record RDABundleSenderConfig(
    /* */
    @NotNull HttpClientConfig server,

    /* */
    String project) {}
