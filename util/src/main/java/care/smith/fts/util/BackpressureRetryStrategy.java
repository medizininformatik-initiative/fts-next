package care.smith.fts.util;

import static reactor.core.Exceptions.retryExhausted;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import reactor.util.retry.Retry.RetrySignal;

@Slf4j
public class BackpressureRetryStrategy implements RetryStrategy {

  private static final int MAX_RETRIES = 3;
  private static final Duration DEFAULT_BACKOFF = Duration.ofSeconds(5);

  private final RetryStrategy delegate;
  private final MeterRegistry meterRegistry;

  public BackpressureRetryStrategy(MeterRegistry meterRegistry, RetryStrategy baseRetryStrategy) {
    this.delegate = baseRetryStrategy;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Retry forRequest(String name) {
    var counter = meterRegistry.counter("http.client.requests.retries", "request_name", name);
    var delegate = this.delegate.forRequest(name);
    return Retry.from(
        companion -> companion.flatMap(signal -> handleSignal(signal, delegate, counter)));
  }

  private static Flux<?> handleSignal(RetrySignal signal, Retry delegate, Counter counter) {
    var failure = signal.failure();

    if (signal.totalRetries() >= MAX_RETRIES) {
      var msg = "Retries exhausted: %d/%d".formatted(MAX_RETRIES, MAX_RETRIES);
      return Flux.error(retryExhausted(msg, failure));
    }

    if (is429TooManyRequests(failure)) {
      counter.increment();
      var delay = extractRetryAfter((WebClientResponseException) failure).orElse(DEFAULT_BACKOFF);
      log.debug("RetrySignal: retrying after {} due to {}", delay, failure.getMessage());
      return Mono.delay(delay).flux();
    }

    return Flux.from(delegate.generateCompanion(Flux.just(signal)));
  }

  private static boolean is429TooManyRequests(Throwable e) {
    return e instanceof WebClientResponseException
        && ((WebClientResponseException) e).getStatusCode().value() == 429;
  }

  private static Optional<Duration> extractRetryAfter(WebClientResponseException wcre) {
    var retryAfter = wcre.getHeaders().getFirst("Retry-After");
    if (retryAfter != null) {
      try {
        return Optional.of(Long.parseLong(retryAfter))
            .filter(seconds -> seconds >= 0)
            .map(Duration::ofSeconds);
      } catch (NumberFormatException ex) {
        log.warn("Failed to parse Retry-After header: {}", retryAfter);
      }
    }
    return Optional.empty();
  }
}
