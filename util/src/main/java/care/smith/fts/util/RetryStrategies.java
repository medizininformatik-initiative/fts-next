package care.smith.fts.util;

import static com.google.common.base.Predicates.or;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

public interface RetryStrategies {

  static RetryBackoffSpec defaultRetryStrategy(MeterRegistry meterRegistry, String name) {
    var counter = meterRegistry.counter("http.client.requests.retries", "request_name", name);
    return Retry.backoff(3, Duration.ofSeconds(1))
        .filter(or(RetryStrategies::is5xxServerError, RetryStrategies::isTimeout))
        .doAfterRetry(i -> counter.increment());
  }

  static boolean isTimeout(Throwable e) {
    return e instanceof TimeoutException;
  }

  private static boolean is5xxServerError(Throwable e) {
    return e instanceof WebClientResponseException
        && ((WebClientResponseException) e).getStatusCode().is5xxServerError();
  }
}
