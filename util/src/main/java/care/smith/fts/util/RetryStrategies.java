package care.smith.fts.util;

import static com.google.common.base.Predicates.or;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

public interface RetryStrategies {
  static RetryBackoffSpec defaultRetryStrategy() {
    return Retry.backoff(3, Duration.ofSeconds(1))
        .filter(or(RetryStrategies::is5xxServerError, RetryStrategies::isTimeout));
  }

  static boolean isTimeout(Throwable e) {
    return e instanceof TimeoutException;
  }

  private static boolean is5xxServerError(Throwable e) {
    return e instanceof WebClientResponseException
        && ((WebClientResponseException) e).getStatusCode().is5xxServerError();
  }
}
