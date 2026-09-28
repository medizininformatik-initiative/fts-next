package care.smith.fts.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class BackpressureRetryStrategyTest {

  /** Mirrors the default backoff the strategy applies when a Retry-After hint is unusable. */
  private static final Duration DEFAULT_BACKOFF = Duration.ofSeconds(5);

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final RetryStrategy retryStrategy =
      new BackpressureRetryStrategy(meterRegistry, new DefaultRetryStrategy(meterRegistry));

  private double retryCount(String name) {
    return meterRegistry.counter("http.client.requests.retries", "request_name", name).count();
  }

  private Mono<String> withRetry(AtomicInteger calls, int failures, Throwable error, String name) {
    return Mono.defer(
            () -> calls.getAndIncrement() < failures ? Mono.error(error) : Mono.just("ok"))
        .retryWhen(retryStrategy.forRequest(name));
  }

  private static WebClientResponseException responseException(int status) {
    return new WebClientResponseException(
        status, "status " + status, new HttpHeaders(), new byte[0], StandardCharsets.UTF_8);
  }

  private static WebClientResponseException responseExceptionWithRetryAfter(
      int status, String retryAfter) {
    var headers = new HttpHeaders();
    headers.add("Retry-After", retryAfter);
    return new WebClientResponseException(
        status, "status " + status, headers, new byte[0], StandardCharsets.UTF_8);
  }

  @Test
  void retriesOn429ThenSucceeds() {
    var calls = new AtomicInteger();
    StepVerifier.withVirtualTime(
            () -> withRetry(calls, 2, responseExceptionWithRetryAfter(429, "1"), "tooMany"))
        .thenAwait(Duration.ofSeconds(60))
        .expectNext("ok")
        .verifyComplete();
    assertThat(retryCount("tooMany")).isEqualTo(2.0);
  }

  @Test
  void retriesOn5xxThenSucceeds() {
    var calls = new AtomicInteger();
    StepVerifier.withVirtualTime(() -> withRetry(calls, 2, responseException(503), "fiveXX"))
        .thenAwait(Duration.ofSeconds(60))
        .expectNext("ok")
        .verifyComplete();
    assertThat(retryCount("fiveXX")).isEqualTo(2.0);
  }

  @Test
  void retriesOnConnectExceptionThenSucceeds() {
    var calls = new AtomicInteger();
    var error =
        new WebClientRequestException(
            new RuntimeException("connect"),
            HttpMethod.GET,
            URI.create("http://example"),
            new HttpHeaders());
    StepVerifier.withVirtualTime(() -> withRetry(calls, 1, error, "connect"))
        .thenAwait(Duration.ofSeconds(60))
        .expectNext("ok")
        .verifyComplete();
    assertThat(retryCount("connect")).isEqualTo(1.0);
  }

  @Test
  void retriesOnTimeoutThenSucceeds() {
    var strategy =
        new BackpressureRetryStrategy(meterRegistry, new DefaultRetryStrategy(meterRegistry, true));
    var calls = new AtomicInteger();
    StepVerifier.withVirtualTime(
            () ->
                Mono.defer(
                        () ->
                            calls.getAndIncrement() < 1
                                ? Mono.error(new TimeoutException("timeout"))
                                : Mono.just("ok"))
                    .retryWhen(strategy.forRequest("timeout")))
        .thenAwait(Duration.ofSeconds(60))
        .expectNext("ok")
        .verifyComplete();
    assertThat(calls.get()).isEqualTo(2);
  }

  @Test
  void doesNotRetryOn4xxExcept429() {
    var calls = new AtomicInteger();
    StepVerifier.withVirtualTime(() -> withRetry(calls, 1, responseException(400), "fourXX"))
        .thenAwait(Duration.ofSeconds(60))
        .expectError(WebClientResponseException.class)
        .verify();
    assertThat(calls.get()).isEqualTo(1);
    assertThat(retryCount("fourXX")).isZero();
  }

  @Test
  void doesNotRetryOn404() {
    var calls = new AtomicInteger();
    StepVerifier.withVirtualTime(() -> withRetry(calls, 1, responseException(404), "notFound"))
        .thenAwait(Duration.ofSeconds(60))
        .expectError(WebClientResponseException.class)
        .verify();
    assertThat(calls.get()).isEqualTo(1);
    assertThat(retryCount("notFound")).isZero();
  }

  @Test
  void doesNotRetryOn3xx() {
    var calls = new AtomicInteger();
    StepVerifier.withVirtualTime(() -> withRetry(calls, 1, responseException(307), "threeXX"))
        .thenAwait(Duration.ofSeconds(60))
        .expectError(WebClientResponseException.class)
        .verify();
    assertThat(calls.get()).isEqualTo(1);
    assertThat(retryCount("threeXX")).isZero();
  }

  @Test
  void exhaustsAfterRetries() {
    var calls = new AtomicInteger();
    StepVerifier.withVirtualTime(
            () -> withRetry(calls, Integer.MAX_VALUE, responseException(429), "exhaust"))
        .thenAwait(Duration.ofSeconds(60))
        .expectErrorMatches(Exceptions::isRetryExhausted)
        .verify();
    assertThat(calls.get()).isEqualTo(4);
    assertThat(retryCount("exhaust")).isEqualTo(3.0);
  }

  /**
   * A Retry-After header a 429 may carry, and the backoff the strategy must apply for it. Only a
   * non-negative hint is followed; an absent, unparsable or negative one falls back to the default
   * backoff.
   */
  static Stream<Arguments> retryAfterHeadersAndBackoffs() {
    return Stream.of(
        arguments(named("10s is honoured", "10"), Duration.ofSeconds(10)),
        arguments(named("zero is honoured", "0"), Duration.ZERO),
        arguments(named("absent falls back", null), DEFAULT_BACKOFF),
        arguments(named("unparsable falls back", "not-a-number"), DEFAULT_BACKOFF),
        arguments(named("negative falls back", "-1"), DEFAULT_BACKOFF));
  }

  /**
   * Asserts that the retry happens exactly at {@code expected}. Pinning the instant, rather than
   * only the outcome, is what separates "the header is read" from "the default is used": for the
   * 10s case a strategy that ignores the header retries at 5s and fails here.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("retryAfterHeadersAndBackoffs")
  void retriesAfterTheBackoffTheHeaderImplies(String retryAfter, Duration expected) {
    var calls = new AtomicInteger();
    var error =
        retryAfter == null
            ? responseException(429)
            : responseExceptionWithRetryAfter(429, retryAfter);

    var step =
        StepVerifier.withVirtualTime(() -> withRetry(calls, 1, error, "retryAfter"))
            .expectSubscription();
    if (!expected.isZero()) {
      step = step.expectNoEvent(expected.minusMillis(1));
    }
    step.thenAwait(Duration.ofMillis(1)).expectNext("ok").verifyComplete();

    assertThat(calls.get()).isEqualTo(2);
  }
}
