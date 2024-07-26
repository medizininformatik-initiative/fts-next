package care.smith.fts.util;

import java.time.Duration;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

public interface RetryStrategies {
  static RetryBackoffSpec defaultRetryStrategy() {
    return Retry.backoff(3, Duration.ofSeconds(1))
        .filter(
            e ->
                e instanceof WebClientResponseException
                    && ((WebClientResponseException) e).getStatusCode().is5xxServerError());
  }
}
