package care.smith.fts.cda.impl;

import care.smith.fts.util.HttpClientConfig;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

public record RdaBundleSenderConfig(
    /* */
    @NotNull HttpClientConfig server,

    /* */
    String project,

    /* Total time a single bundle may spend waiting on RDA 429 backpressure before giving up. */
    Duration maxBackpressureWait) {

  private static final Duration DEFAULT_MAX_BACKPRESSURE_WAIT = Duration.ofMinutes(10);

  public RdaBundleSenderConfig {
    if (maxBackpressureWait == null) {
      maxBackpressureWait = DEFAULT_MAX_BACKPRESSURE_WAIT;
    }
  }

  public RdaBundleSenderConfig(HttpClientConfig server, String project) {
    this(server, project, DEFAULT_MAX_BACKPRESSURE_WAIT);
  }
}
