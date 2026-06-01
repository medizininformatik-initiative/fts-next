package care.smith.fts.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RdaBackpressureRetryStrategyTest {

  private static final String NAME = "testRequest";

  private static reactor.util.retry.Retry backpressure(MeterRegistry registry) {
    return new RdaBackpressureRetryStrategy(registry).forRequest(NAME);
  }

  private static reactor.util.retry.Retry backpressure(MeterRegistry registry, Duration maxWait) {
    return new RdaBackpressureRetryStrategy(registry, maxWait).forRequest(NAME);
  }

  private static reactor.util.retry.Retry backpressure(
      MeterRegistry registry, boolean retryTimeout) {
    return new RdaBackpressureRetryStrategy(
            registry, RdaBackpressureRetryStrategy.DEFAULT_MAX_BACKPRESSURE_WAIT, retryTimeout)
        .forRequest(NAME);
  }

  private static WebClientResponseException responseException(
      HttpStatusCode status, HttpHeaders headers) {
    return new WebClientResponseException(
        status, status.toString(), headers, new byte[0], StandardCharsets.UTF_8, null);
  }

  private static WebClientResponseException tooManyRequests(String retryAfter) {
    var headers = new HttpHeaders();
    if (retryAfter != null) {
      headers.add(HttpHeaders.RETRY_AFTER, retryAfter);
    }
    return responseException(HttpStatus.TOO_MANY_REQUESTS, headers);
  }

  /** Emits the given failure for the first {@code failures} subscriptions, then succeeds. */
  private static Mono<String> failingThenSucceeding(int failures, Throwable error) {
    var attempts = new AtomicInteger();
    return Mono.defer(
        () -> attempts.getAndIncrement() < failures ? Mono.error(error) : Mono.just("ok"));
  }

  @Test
  void backpressureRetryOn429ThenSucceeds() {
    MeterRegistry registry = new SimpleMeterRegistry();
    var mono = failingThenSucceeding(1, tooManyRequests(null)).retryWhen(backpressure(registry));

    StepVerifier.withVirtualTime(() -> mono)
        .thenAwait(Duration.ofSeconds(10))
        .expectNext("ok")
        .verifyComplete();

    assertThat(registry.counter("http.client.requests.retries", "request_name", NAME).count())
        .isEqualTo(1.0);
  }

  @Test
  void backpressureRetryAfterActsAsFloor() {
    MeterRegistry registry = new SimpleMeterRegistry();
    var mono = failingThenSucceeding(1, tooManyRequests("5")).retryWhen(backpressure(registry));

    // First retry exponential backoff is ~1s; Retry-After: 5 must lengthen it to >=5s. Upward-only
    // jitter (<=50%) can stretch it to at most 7.5s, but must never fire before the 5s floor.
    StepVerifier.withVirtualTime(() -> mono)
        .expectSubscription()
        .expectNoEvent(Duration.ofMillis(4999))
        .thenAwait(Duration.ofSeconds(3))
        .expectNext("ok")
        .verifyComplete();
  }

  @Test
  void backpressureNoRetryAfterFallsBackToBackoff() {
    MeterRegistry registry = new SimpleMeterRegistry();
    var mono = failingThenSucceeding(1, tooManyRequests(null)).retryWhen(backpressure(registry));

    // No Retry-After -> exponential backoff (~1s). Should not fire before the backoff elapses.
    StepVerifier.withVirtualTime(() -> mono)
        .expectSubscription()
        .expectNoEvent(Duration.ofMillis(900))
        .thenAwait(Duration.ofSeconds(2))
        .expectNext("ok")
        .verifyComplete();
  }

  @Test
  void backpressureDoesNotRetryNonRetryableError() {
    MeterRegistry registry = new SimpleMeterRegistry();
    var error = responseException(HttpStatus.BAD_REQUEST, new HttpHeaders());
    var mono = failingThenSucceeding(1, error).retryWhen(backpressure(registry));

    StepVerifier.withVirtualTime(() -> mono).expectErrorMatches(t -> t == error).verify();

    assertThat(registry.counter("http.client.requests.retries", "request_name", NAME).count())
        .isZero();
  }

  @Test
  void backpressure429RetriesBeyondAttemptCap() {
    // 429 must NOT be capped by MAX_ATTEMPTS: 5 backpressure responses still succeed.
    MeterRegistry registry = new SimpleMeterRegistry();
    var mono = failingThenSucceeding(5, tooManyRequests("1")).retryWhen(backpressure(registry));

    StepVerifier.withVirtualTime(() -> mono)
        .thenAwait(Duration.ofMinutes(1))
        .expectNext("ok")
        .verifyComplete();

    assertThat(registry.counter("http.client.requests.retries", "request_name", NAME).count())
        .isEqualTo(5.0);
  }

  @Test
  void backpressure429ExhaustsWhenMaxWaitExceeded() {
    // With a 3s budget and a 1s Retry-After floor per retry, the cumulative wait soon exceeds it.
    MeterRegistry registry = new SimpleMeterRegistry();
    var mono =
        failingThenSucceeding(Integer.MAX_VALUE, tooManyRequests("1"))
            .retryWhen(backpressure(registry, Duration.ofSeconds(3)));

    StepVerifier.withVirtualTime(() -> mono)
        .thenAwait(Duration.ofMinutes(1))
        .expectErrorMatches(Exceptions::isRetryExhausted)
        .verify();
  }

  @Test
  void timeoutRetriedWhenRetryTimeoutEnabled() {
    // retryTimeout=true: a TimeoutException is retryable (covers isTimeout's enabled+instanceof
    // branches independently of the ambient fts.retryTimeout system property).
    MeterRegistry registry = new SimpleMeterRegistry();
    var mono =
        failingThenSucceeding(1, new TimeoutException("timeout"))
            .retryWhen(backpressure(registry, true));

    StepVerifier.withVirtualTime(() -> mono)
        .thenAwait(Duration.ofSeconds(10))
        .expectNext("ok")
        .verifyComplete();

    assertThat(registry.counter("http.client.requests.retries", "request_name", NAME).count())
        .isEqualTo(1.0);
  }

  @Test
  void timeoutNotRetriedWhenRetryTimeoutDisabled() {
    // retryTimeout=false short-circuits isTimeout (covers the disabled branch): a TimeoutException
    // is no longer retryable and propagates immediately, regardless of the ambient flag value.
    MeterRegistry registry = new SimpleMeterRegistry();
    var error = new TimeoutException("timeout");
    var mono = failingThenSucceeding(1, error).retryWhen(backpressure(registry, false));

    StepVerifier.withVirtualTime(() -> mono).expectErrorMatches(t -> t == error).verify();

    assertThat(registry.counter("http.client.requests.retries", "request_name", NAME).count())
        .isZero();
  }

  @Test
  void backpressureRetriesConnectException() {
    // Connect failure is retryable (non-429): exercises the isConnectException predicate branch.
    // Pinned to retryTimeout=true so isTimeout evaluates its instanceof check against a non-timeout
    // error (covers the instanceof==false branch independently of the ambient fts.retryTimeout).
    MeterRegistry registry = new SimpleMeterRegistry();
    var error =
        new WebClientRequestException(
            new IOException("connection refused"),
            HttpMethod.POST,
            URI.create("http://hds"),
            new HttpHeaders());
    var mono = failingThenSucceeding(1, error).retryWhen(backpressure(registry, true));

    StepVerifier.withVirtualTime(() -> mono)
        .thenAwait(Duration.ofSeconds(10))
        .expectNext("ok")
        .verifyComplete();

    assertThat(registry.counter("http.client.requests.retries", "request_name", NAME).count())
        .isEqualTo(1.0);
  }

  @Test
  void transientErrorExhaustsAtAttemptCap() {
    // 5xx keeps the attempt cap: persistent infra failure gives up after MAX_ATTEMPTS.
    MeterRegistry registry = new SimpleMeterRegistry();
    var error = responseException(HttpStatus.SERVICE_UNAVAILABLE, new HttpHeaders());
    var mono = failingThenSucceeding(Integer.MAX_VALUE, error).retryWhen(backpressure(registry));

    StepVerifier.withVirtualTime(() -> mono)
        .thenAwait(Duration.ofMinutes(1))
        .expectErrorMatches(Exceptions::isRetryExhausted)
        .verify();
  }

  @Test
  void backpressureUnparseableRetryAfterFallsBackToBackoff() {
    // A non-numeric Retry-After must be ignored (no floor), falling back to exponential backoff.
    MeterRegistry registry = new SimpleMeterRegistry();
    var mono =
        failingThenSucceeding(1, tooManyRequests("not-a-number")).retryWhen(backpressure(registry));

    StepVerifier.withVirtualTime(() -> mono)
        .expectSubscription()
        .expectNoEvent(Duration.ofMillis(900))
        .thenAwait(Duration.ofSeconds(2))
        .expectNext("ok")
        .verifyComplete();

    assertThat(registry.counter("http.client.requests.retries", "request_name", NAME).count())
        .isEqualTo(1.0);
  }

  @Test
  void jitterIsANoOpForSubMillisecondDelays() throws Exception {
    // Guard branch: when delay * jitterFactor rounds to <= 0 the delay is returned unchanged. The
    // public strategy never produces so small a delay (min backoff is 1s), so exercise it directly.
    Method jitter = RdaBackpressureRetryStrategy.class.getDeclaredMethod("jitter", Duration.class);
    jitter.setAccessible(true);

    assertThat(jitter.invoke(null, Duration.ZERO)).isEqualTo(Duration.ZERO);
    assertThat(jitter.invoke(null, Duration.ofMillis(1))).isEqualTo(Duration.ofMillis(1));
  }

  @Test
  void defaultStrategyStillDoesNotRetry429() {
    MeterRegistry registry = new SimpleMeterRegistry();
    var mono =
        failingThenSucceeding(1, tooManyRequests("1"))
            .retryWhen(new DefaultRetryStrategy(registry).forRequest(NAME));

    StepVerifier.withVirtualTime(() -> mono)
        .thenAwait(Duration.ofSeconds(10))
        .expectErrorMatches(t -> t instanceof WebClientResponseException)
        .verify();

    assertThat(registry.counter("http.client.requests.retries", "request_name", NAME).count())
        .isZero();
  }
}
