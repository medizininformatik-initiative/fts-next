package care.smith.fts.cda.impl;

import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
   * A Retry-After header the RDA may send, and the delay the sender must apply for it. Only a
   * usable hint is followed; an absent, unparsable or negative one falls back to 1s, so that a
   * malformed hint can never poll the RDA harder than the default.
   */
  static Stream<Arguments> retryAfterHeadersAndDelays() {
    return Stream.of(
        arguments(named("2s is honoured", "2"), Duration.ofSeconds(2)),
        arguments(named("absent falls back", null), Duration.ofSeconds(1)),
        arguments(named("unparsable falls back", "try to parse this!"), Duration.ofSeconds(1)),
        arguments(named("negative falls back", "-1"), Duration.ofSeconds(1)));
  }

  /**
   * Asserts that the send emits nothing until {@code expected} has passed, and completes then. The
   * stubbed exchange answers instantly, so the elapsed time is the delay the sender applied, and
   * nothing else. A sender that drops the header finishes early and fails here.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("retryAfterHeadersAndDelays")
  void defersNextPollByRetryAfter(String retryAfter, Duration expected) {
    var sender = new RdaBundleSender(CONFIG, clientDeferringOnce(retryAfter), buildRetryStrategy());

    StepVerifier.withVirtualTime(() -> sender.send(new TransportBundle(new Bundle(), "tid")))
        .expectSubscription()
        .expectNoEvent(expected.minusMillis(1))
        .thenAwait(Duration.ofMillis(1))
        .expectNextCount(1)
        .verifyComplete();
  }

  /** Every shape of Content-Location that names no usable status URI, so polling cannot start. */
  static Stream<Arguments> unusableContentLocations() {
    return Stream.of(
        arguments(named("header absent", null)),
        arguments(named("value is blank", List.of("   "))),
        arguments(named("value list is empty", List.of())));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("unusableContentLocations")
  void unusableContentLocationRaisesTransferProcessException(List<String> contentLocation) {
    var client =
        buildClient(
            request ->
                ClientResponse.create(HttpStatus.ACCEPTED)
                    .headers(
                        h ->
                            Optional.ofNullable(contentLocation)
                                .ifPresent(v -> h.put(CONTENT_LOCATION, v)))
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
