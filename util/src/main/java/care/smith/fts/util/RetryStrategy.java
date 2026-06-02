package care.smith.fts.util;

import reactor.util.retry.Retry;

/** Builds the reactor {@link Retry} spec for a named outbound request. */
public interface RetryStrategy {

  /** Returns the retry spec for call-site {@code name} (used as the {@code request_name} tag). */
  Retry forRequest(String name);
}
