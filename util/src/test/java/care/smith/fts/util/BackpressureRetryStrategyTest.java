package care.smith.fts.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class BackpressureRetryStrategyTest {

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

  @Test
  void honorsRetryAfterHeader() {
    var calls = new AtomicInteger();
    StepVerifier.withVirtualTime(
            () ->
                withRetry(calls, 1, responseExceptionWithRetryAfter(429, "10"), "retryAfterHeader"))
        .thenAwait(Duration.ofSeconds(10))
        .expectNext("ok")
        .verifyComplete();
    assertThat(calls.get()).isEqualTo(2);
  }

  @Test
  void usesDefaultBackoffWhenRetryAfterMissing() {
    var calls = new AtomicInteger();
    StepVerifier.withVirtualTime(() -> withRetry(calls, 1, responseException(429), "noRetryAfter"))
        .thenAwait(Duration.ofSeconds(5))
        .expectNext("ok")
        .verifyComplete();
    assertThat(calls.get()).isEqualTo(2);
  }

  @Test
  void usesDefaultBackoffWhenRetryAfterUnparseable() {
    var calls = new AtomicInteger();
    StepVerifier.withVirtualTime(
            () ->
                withRetry(
                    calls,
                    1,
                    responseExceptionWithRetryAfter(429, "not-a-number"),
                    "badRetryAfter"))
        .thenAwait(Duration.ofSeconds(5))
        .expectNext("ok")
        .verifyComplete();
    assertThat(calls.get()).isEqualTo(2);
  }

  @Test
  void usesDefaultBackoffWhenRetryAfterNegative() {
    var calls = new AtomicInteger();
    StepVerifier.withVirtualTime(
            () ->
                withRetry(
                    calls, 1, responseExceptionWithRetryAfter(429, "-1"), "negativeRetryAfter"))
        .thenAwait(Duration.ofSeconds(5))
        .expectNext("ok")
        .verifyComplete();
    assertThat(calls.get()).isEqualTo(2);
  }
}
