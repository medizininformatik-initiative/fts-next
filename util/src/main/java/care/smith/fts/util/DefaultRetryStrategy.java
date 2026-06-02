package care.smith.fts.util;

import static com.google.common.base.Predicates.or;
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
        .filter(
            or(
                DefaultRetryStrategy::is5xxServerError,
                this::isTimeout,
                DefaultRetryStrategy::isConnectException))
        .doAfterRetry(i -> counter.increment());
  }

  private boolean isTimeout(Throwable e) {
    return retryTimeout && e instanceof TimeoutException;
  }

  private static boolean is5xxServerError(Throwable e) {
    return e instanceof WebClientResponseException
        && ((WebClientResponseException) e).getStatusCode().is5xxServerError();
  }

  private static boolean isConnectException(Throwable e) {
    return e instanceof WebClientRequestException;
  }
}
