package care.smith.fts.rda.impl;

import care.smith.fts.util.HttpClientConfig;
import jakarta.validation.constraints.NotNull;

public record FhirStoreBundleSenderConfig(
    /* */
    @NotNull HttpClientConfig server,

    /* */
    String project,

    /* Maximum concurrent sends this RDA performs against the HDS (per-HDS drain concurrency). */
    Integer maxConcurrency) {

  private static final int DEFAULT_MAX_CONCURRENCY = 2;

  public FhirStoreBundleSenderConfig {
    if (maxConcurrency == null) {
      maxConcurrency = DEFAULT_MAX_CONCURRENCY;
    }
  }

  public FhirStoreBundleSenderConfig(HttpClientConfig server, String project) {
    this(server, project, DEFAULT_MAX_CONCURRENCY);
  }
}
