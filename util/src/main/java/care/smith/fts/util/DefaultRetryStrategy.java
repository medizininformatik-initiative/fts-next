package care.smith.fts.util;

import static java.lang.Boolean.parseBoolean;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

/** Default {@link RetryStrategy}: 3 attempts, 1s backoff, retrying 5xx / timeout / connect. */
@Slf4j
public class DefaultRetryStrategy implements RetryStrategy {

  private final MeterRegistry meterRegistry;
  private final boolean retryTimeout;

  public DefaultRetryStrategy(MeterRegistry meterRegistry) {
    this(meterRegistry, parseBoolean(System.getProperty("fts.retryTimeout", "true")));
  }

  public DefaultRetryStrategy(MeterRegistry meterRegistry, boolean retryTimeout) {
    this.meterRegistry = meterRegistry;
    this.retryTimeout = retryTimeout;
  }

  @Override
  public Retry forRequest(String name) {
    var counter = meterRegistry.counter("http.client.requests.retries", "request_name", name);
    return Retry.backoff(3, Duration.ofSeconds(1))
        .doBeforeRetry(retry -> log.debug("RetrySignal {}", retry))
        .filter(this::isRetryable)
        .doAfterRetry(i -> counter.increment());
  }

  private boolean isRetryable(Throwable e) {
    // A 3xx that reaches here was not followed by the transport (policy DONT_FOLLOW, or an
    // HTTPS->HTTP downgrade refused by FOLLOW_SAFE). Re-issuing the identical request only yields
    // another redirect, not the resource, so it is terminal, never retryable; classified
    // explicitly so it can never slip into a retry (#1706).
    if (is3xxRedirection(e)) {
      return false;
    }
    return is5xxServerError(e) || isTimeout(e) || isConnectException(e);
  }

  private boolean isTimeout(Throwable e) {
    return retryTimeout && e instanceof TimeoutException;
  }

  private static boolean is3xxRedirection(Throwable e) {
    return e instanceof WebClientResponseException
        && ((WebClientResponseException) e).getStatusCode().is3xxRedirection();
  }

  private static boolean is5xxServerError(Throwable e) {
    return e instanceof WebClientResponseException
        && ((WebClientResponseException) e).getStatusCode().is5xxServerError();
  }

  private static boolean isConnectException(Throwable e) {
    return e instanceof WebClientRequestException;
  }
}
