package care.smith.fts.util;

import static java.lang.Boolean.parseBoolean;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * {@link RetryStrategy} for sending bundles to the research-domain-agent (RDA).
 *
 * <p>Retries on the same conditions as {@link DefaultRetryStrategy} (5xx, timeout, connect) plus
 * HTTP 429 (Too Many Requests), which the RDA emits as backpressure when its bundle buffer is full.
 * The two classes of failure are treated differently:
 *
 * <ul>
 *   <li><b>Transient infra errors</b> (5xx, timeout, connect) keep the {@link #MAX_ATTEMPTS}
 *       attempt cap — they signal a problem unlikely to clear by waiting longer.
 *   <li><b>429 backpressure</b> is <em>not</em> capped by attempts: the RDA is explicitly asking
 *       the CDA to slow down, so the bundle keeps retrying until the cumulative backpressure wait
 *       would exceed {@code maxBackpressureWait}, then gives up. The {@code Retry-After} header
 *       (integer seconds) is a <em>floor</em> on each wait ({@code max(exponentialBackoff,
 *       retryAfter)}); the exponential backoff is capped at {@link #MAX_BACKOFF} so the retries
 *       settle into a steady polling cadence rather than diverging.
 * </ul>
 *
 * Every delay carries upward-only jitter (see {@link #JITTER_FACTOR}). An absent or unparseable
 * {@code Retry-After} falls back to the (jittered) exponential backoff.
 */
@Slf4j
public class RdaBackpressureRetryStrategy implements RetryStrategy {

  static final long MAX_ATTEMPTS = 3;
  static final Duration MIN_BACKOFF = Duration.ofSeconds(1);
  static final double BACKOFF_MULTIPLIER = 2.0;

  /**
   * Caps the exponential backoff so indefinite 429 retries settle into a steady polling cadence.
   */
  static final Duration MAX_BACKOFF = Duration.ofSeconds(30);

  /**
   * Additive jitter fraction applied on top of the computed delay. Upward-only (never shortens the
   * wait) so a {@code Retry-After} floor stays a hard floor while concurrent CDAs retrying the same
   * 429 desynchronise instead of stampeding the RDA in lockstep.
   */
  static final double JITTER_FACTOR = 0.5;

  /** Total time a single bundle may spend waiting on 429 backpressure before the CDA gives up. */
  static final Duration DEFAULT_MAX_BACKPRESSURE_WAIT = Duration.ofMinutes(10);

  private final MeterRegistry meterRegistry;
  private final Duration maxBackpressureWait;
  private final boolean retryTimeout;

  public RdaBackpressureRetryStrategy(MeterRegistry meterRegistry) {
    this(meterRegistry, DEFAULT_MAX_BACKPRESSURE_WAIT);
  }

  public RdaBackpressureRetryStrategy(MeterRegistry meterRegistry, Duration maxBackpressureWait) {
    this(
        meterRegistry,
        maxBackpressureWait,
        parseBoolean(System.getProperty("fts.retryTimeout", "true")));
  }

  public RdaBackpressureRetryStrategy(
      MeterRegistry meterRegistry, Duration maxBackpressureWait, boolean retryTimeout) {
    this.meterRegistry = meterRegistry;
    this.maxBackpressureWait = maxBackpressureWait;
    this.retryTimeout = retryTimeout;
  }

  @Override
  public Retry forRequest(String name) {
    var counter = meterRegistry.counter("http.client.requests.retries", "request_name", name);
    return Retry.from(
        companion -> {
          // Per-subscription cumulative 429 wait; bounds indefinite backpressure retries.
          var backpressureWaitMillis = new AtomicLong();
          return companion.flatMap(
              retrySignal -> handleRetrySignal(retrySignal, counter, backpressureWaitMillis));
        });
  }

  private Mono<Long> handleRetrySignal(
      Retry.RetrySignal signal, Counter counter, AtomicLong backpressureWaitMillis) {
    var failure = signal.failure();
    if (!isRdaBackpressureRetryable(failure)) {
      return Mono.error(failure);
    }
    var backpressure = isTooManyRequests(failure);
    if (!backpressure && signal.totalRetries() >= MAX_ATTEMPTS) {
      return Mono.error(
          Exceptions.retryExhausted(
              "Retries exhausted: " + signal.totalRetries() + "/" + MAX_ATTEMPTS, failure));
    }

    var delay = jitter(effectiveDelay(exponentialBackoff(signal.totalRetries()), failure));

    if (backpressure
        && backpressureWaitMillis.addAndGet(delay.toMillis()) > maxBackpressureWait.toMillis()) {
      return Mono.error(
          Exceptions.retryExhausted(
              "RDA backpressure wait exceeded " + maxBackpressureWait, failure));
    }

    log.debug("RetrySignal {} (attempt {}, delay {})", failure, signal.totalRetries() + 1, delay);
    return Mono.delay(delay).doOnNext(i -> counter.increment());
  }

  private static Duration exponentialBackoff(long retriesSoFar) {
    var millis = MIN_BACKOFF.toMillis() * Math.pow(BACKOFF_MULTIPLIER, retriesSoFar);
    return Duration.ofMillis((long) Math.min(millis, (double) MAX_BACKOFF.toMillis()));
  }

  /** Adds {@code U[0, delay * jitterFactor]} to the delay, never reducing it. */
  private static Duration jitter(Duration delay) {
    long span = (long) (delay.toMillis() * JITTER_FACTOR);
    if (span <= 0) {
      return delay;
    }
    return delay.plusMillis(ThreadLocalRandom.current().nextLong(span + 1));
  }

  /** Applies {@code Retry-After} as a floor on the backoff for 429 responses. */
  private static Duration effectiveDelay(Duration backoff, Throwable failure) {
    return retryAfterSeconds(failure)
        .map(seconds -> max(backoff, Duration.ofSeconds(seconds)))
        .orElse(backoff);
  }

  private static Duration max(Duration a, Duration b) {
    return a.compareTo(b) >= 0 ? a : b;
  }

  /**
   * Extracts the {@code Retry-After} value (integer seconds) from a 429 response. HTTP-date form is
   * out of scope; an absent or unparseable value yields {@link Optional#empty()}.
   */
  private static Optional<Long> retryAfterSeconds(Throwable failure) {
    if (!isTooManyRequests(failure)) {
      return Optional.empty();
    }
    var header = ((WebClientResponseException) failure).getHeaders().getFirst(RETRY_AFTER);
    if (header == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(Long.parseLong(header.trim()));
    } catch (NumberFormatException e) {
      log.warn("Failed to parse Retry-After header: {}", header);
      return Optional.empty();
    }
  }

  private boolean isRdaBackpressureRetryable(Throwable e) {
    return is5xxServerError(e) || isTimeout(e) || isConnectException(e) || isTooManyRequests(e);
  }

  private static boolean isTooManyRequests(Throwable e) {
    return e instanceof WebClientResponseException
        && ((WebClientResponseException) e).getStatusCode().value()
            == HttpStatus.TOO_MANY_REQUESTS.value();
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
