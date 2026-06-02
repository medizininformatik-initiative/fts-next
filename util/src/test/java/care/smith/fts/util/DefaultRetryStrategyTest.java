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

class DefaultRetryStrategyTest {

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
  private final RetryStrategy retryStrategy = new DefaultRetryStrategy(meterRegistry);

  private double retryCount(String name) {
    return meterRegistry.counter("http.client.requests.retries", "request_name", name).count();
  }

  private Mono<String> withRetry(AtomicInteger calls, int failures, Throwable error, String name) {
    return withRetry(retryStrategy, calls, failures, error, name);
  }

  private Mono<String> withRetry(
      RetryStrategy strategy, AtomicInteger calls, int failures, Throwable error, String name) {
    return Mono.defer(
            () -> calls.getAndIncrement() < failures ? Mono.error(error) : Mono.just("ok"))
        .retryWhen(strategy.forRequest(name));
  }

  private static WebClientResponseException responseException(int status) {
    return new WebClientResponseException(
        status, "status " + status, new HttpHeaders(), new byte[0], StandardCharsets.UTF_8);
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
    // Built with retryTimeout=true explicitly so the assertion holds regardless of the
    // build-wide -Dfts.retryTimeout flag (set to false during connection-scenario ITs).
    var strategy = new DefaultRetryStrategy(meterRegistry, true);
    var calls = new AtomicInteger();
    StepVerifier.withVirtualTime(
            () -> withRetry(strategy, calls, 1, new TimeoutException("timeout"), "timeout"))
        .thenAwait(Duration.ofSeconds(60))
        .expectNext("ok")
        .verifyComplete();
    assertThat(retryCount("timeout")).isEqualTo(1.0);
  }

  @Test
  void doesNotRetryTimeoutWhenDisabled() {
    var strategy = new DefaultRetryStrategy(meterRegistry, false);
    var calls = new AtomicInteger();
    StepVerifier.withVirtualTime(
            () -> withRetry(strategy, calls, 1, new TimeoutException("timeout"), "noTimeout"))
        .thenAwait(Duration.ofSeconds(60))
        .expectError(TimeoutException.class)
        .verify();
    assertThat(calls.get()).isEqualTo(1);
    assertThat(retryCount("noTimeout")).isZero();
  }

  @Test
  void doesNotRetryNonTimeoutWhenTimeoutEnabled() {
    // retryTimeout=true but the error is not a TimeoutException, exercising the
    // `e instanceof TimeoutException` false branch independent of the build-wide flag.
    var strategy = new DefaultRetryStrategy(meterRegistry, true);
    var calls = new AtomicInteger();
    StepVerifier.withVirtualTime(
            () -> withRetry(strategy, calls, 1, responseException(400), "nonTimeout"))
        .thenAwait(Duration.ofSeconds(60))
        .expectError(WebClientResponseException.class)
        .verify();
    assertThat(calls.get()).isEqualTo(1);
    assertThat(retryCount("nonTimeout")).isZero();
  }

  @Test
  void doesNotRetryOn4xx() {
    var calls = new AtomicInteger();
    StepVerifier.withVirtualTime(() -> withRetry(calls, 1, responseException(400), "fourXX"))
        .thenAwait(Duration.ofSeconds(60))
        .expectError(WebClientResponseException.class)
        .verify();
    assertThat(calls.get()).isEqualTo(1);
    assertThat(retryCount("fourXX")).isZero();
  }

  @Test
  void exhaustsAfterThreeRetries() {
    var calls = new AtomicInteger();
    StepVerifier.withVirtualTime(
            () -> withRetry(calls, Integer.MAX_VALUE, responseException(500), "exhaust"))
        .thenAwait(Duration.ofSeconds(60))
        .expectErrorMatches(Exceptions::isRetryExhausted)
        .verify();
    assertThat(calls.get()).isEqualTo(4);
    assertThat(retryCount("exhaust")).isEqualTo(3.0);
  }
}
