package care.smith.fts.cda.impl;

import care.smith.fts.util.HTTPClientConfig;
import jakarta.validation.constraints.NotNull;

public record RDABundleSenderConfig(
    /* */
    @NotNull HTTPClientConfig server,

    /* */
    String project) {}
