package care.smith.fts.cda.impl;

import static org.springframework.http.HttpHeaders.CONTENT_LOCATION;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.util.BackpressureRetryStrategy;
import care.smith.fts.util.DefaultRetryStrategy;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.error.TransferProcessException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class RdaBundleSenderTest {

  private static final RdaBundleSenderConfig CONFIG =
      new RdaBundleSenderConfig(new HttpClientConfig("http://localhost"), "example");
  private static final String STATUS_URI = "/api/v2/process/status/proc-1";

  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  private static WebClient buildClient(Function<ClientRequest, ClientResponse> handler) {
    ExchangeFunction exchange = request -> Mono.just(handler.apply(request));
    return WebClient.builder().exchangeFunction(exchange).build();
  }

  /**
   * POST is ACCEPTED and names a status URI. The first status poll defers with {@code retryAfter}
   * as its Retry-After header, or with no such header when {@code retryAfter} is null. Every later
   * poll is OK.
   */
  private static WebClient clientDeferringOnce(String retryAfter) {
    var polls = new AtomicInteger();
    return buildClient(
        request -> {
          if (request.method() == HttpMethod.POST) {
            return ClientResponse.create(HttpStatus.ACCEPTED)
                .header(CONTENT_LOCATION, STATUS_URI)
                .build();
          }
          if (polls.getAndIncrement() > 0) {
            return ClientResponse.create(HttpStatus.OK).build();
          }
          var accepted = ClientResponse.create(HttpStatus.ACCEPTED);
          return retryAfter == null
              ? accepted.build()
              : accepted.header(RETRY_AFTER, retryAfter).build();
        });
  }

  /**
   * Asserts that a send against {@link #clientDeferringOnce} emits nothing until {@code expected}
   * has passed, and completes then. The stubbed exchange answers instantly, so the elapsed time is
   * the delay the sender applied, and nothing else.
   */
  private void assertPollDeferredBy(String retryAfter, Duration expected) {
    var sender = new RdaBundleSender(CONFIG, clientDeferringOnce(retryAfter), buildRetryStrategy());

    StepVerifier.withVirtualTime(() -> sender.send(new TransportBundle(new Bundle(), "tid")))
        .expectSubscription()
        .expectNoEvent(expected.minusMillis(1))
        .thenAwait(Duration.ofMillis(1))
        .expectNextCount(1)
        .verifyComplete();
  }

  @Test
  void waitsTheRetryAfterTheRdaAsksFor() {
    // 2s is longer than the 1s fallback, so a sender that drops the header fails this.
    assertPollDeferredBy("2", Duration.ofSeconds(2));
  }

  @Test
  void waitsOneSecondWhenRetryAfterIsMissing() {
    assertPollDeferredBy(null, Duration.ofSeconds(1));
  }

  @Test
  void waitsOneSecondWhenRetryAfterIsUnparsable() {
    assertPollDeferredBy("try to parse this!", Duration.ofSeconds(1));
  }

  @Test
  void waitsOneSecondWhenRetryAfterIsNegative() {
    // A negative hint must not shorten the poll interval below the fallback, otherwise an RDA
    // under load can be polled back to back.
    assertPollDeferredBy("-1", Duration.ofSeconds(1));
  }

  /** POST is ACCEPTED but names no usable status URI, so polling can never start. */
  private void assertMissingContentLocation(String contentLocation) {
    var client =
        buildClient(
            request -> {
              var accepted = ClientResponse.create(HttpStatus.ACCEPTED);
              return contentLocation == null
                  ? accepted.build()
                  : accepted.header(CONTENT_LOCATION, contentLocation).build();
            });
    var sender = new RdaBundleSender(CONFIG, client, buildRetryStrategy());

    create(sender.send(new TransportBundle(new Bundle(), "tid")))
        .expectErrorMatches(
            e ->
                e instanceof TransferProcessException
                    && e.getMessage().equals("Missing Content-Location"))
        .verify();
  }

  @Test
  void absentContentLocationRaisesTransferProcessException() {
    assertMissingContentLocation(null);
  }

  @Test
  void blankContentLocationRaisesTransferProcessException() {
    // A header that is present but all whitespace is as unusable as an absent one.
    assertMissingContentLocation("   ");
  }

  @Test
  void emptyContentLocationListRaisesTransferProcessException() {
    // The header name is present but carries no value at all. Reading the first element of that
    // list would throw, so the sender must reject it as missing instead.
    var client =
        buildClient(
            request ->
                ClientResponse.create(HttpStatus.ACCEPTED)
                    .headers(h -> h.put(CONTENT_LOCATION, List.of()))
                    .build());
    var sender = new RdaBundleSender(CONFIG, client, buildRetryStrategy());

    create(sender.send(new TransportBundle(new Bundle(), "tid")))
        .expectErrorMatches(
            e ->
                e instanceof TransferProcessException
                    && e.getMessage().equals("Missing Content-Location"))
        .verify();
  }

  @Test
  void pollingBudgetExhaustedRaisesTransferProcessException() {
    var client =
        buildClient(
            request -> {
              if (request.method() == HttpMethod.POST) {
                return ClientResponse.create(HttpStatus.ACCEPTED)
                    .header(CONTENT_LOCATION, STATUS_URI)
                    .build();
              }
              return ClientResponse.create(HttpStatus.ACCEPTED).header(RETRY_AFTER, "0").build();
            });
    var sender = new RdaBundleSender(CONFIG, client, buildRetryStrategy());

    create(sender.send(new TransportBundle(new Bundle(), "tid")))
        .expectErrorMatches(
            e ->
                e instanceof TransferProcessException
                    && e.getMessage().equals("RDA polling budget exhausted"))
        .verify();
  }

  private BackpressureRetryStrategy buildRetryStrategy() {
    return new BackpressureRetryStrategy(meterRegistry, new DefaultRetryStrategy(meterRegistry));
  }

  @Test
  void unexpectedStatusFromPollingEndpointRaisesTransferProcessException() {
    var client =
        buildClient(
            request -> {
              if (request.method() == HttpMethod.POST) {
                return ClientResponse.create(HttpStatus.ACCEPTED)
                    .header(CONTENT_LOCATION, STATUS_URI)
                    .build();
              }
              return ClientResponse.create(HttpStatus.CREATED).build();
            });
    var sender = new RdaBundleSender(CONFIG, client, buildRetryStrategy());

    create(sender.send(new TransportBundle(new Bundle(), "tid")))
        .expectErrorMatches(
            e ->
                e instanceof TransferProcessException
                    && e.getMessage().startsWith("Unexpected RDA status: 201"))
        .verify();
  }

  @Test
  void successfulOkAtFirstPoll() {
    var client =
        buildClient(
            request -> {
              if (request.method() == HttpMethod.POST) {
                return ClientResponse.create(HttpStatus.ACCEPTED)
                    .header(CONTENT_LOCATION, STATUS_URI)
                    .build();
              }
              return ClientResponse.create(HttpStatus.OK).build();
            });
    var sender = new RdaBundleSender(CONFIG, client, buildRetryStrategy());

    create(sender.send(new TransportBundle(new Bundle(), "tid")))
        .expectNextCount(1)
        .verifyComplete();
  }

  @Test
  void postOkSkipsPolling() {
    var client = buildClient(request -> ClientResponse.create(HttpStatus.OK).build());
    var sender = new RdaBundleSender(CONFIG, client, buildRetryStrategy());

    create(sender.send(new TransportBundle(new Bundle(), "tid")))
        .expectNextCount(1)
        .verifyComplete();
  }
}
