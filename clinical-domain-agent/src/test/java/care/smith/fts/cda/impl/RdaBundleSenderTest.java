package care.smith.fts.cda.impl;

import static org.springframework.http.HttpHeaders.CONTENT_LOCATION;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.util.DefaultRetryStrategy;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.error.TransferProcessException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

class RdaBundleSenderTest {

  private static final RdaBundleSenderConfig CONFIG =
      new RdaBundleSenderConfig(new HttpClientConfig("http://localhost"), "example");
  private static final String STATUS_URI = "/api/v2/process/status/proc-1";

  private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

  private static WebClient buildClient(Function<ClientRequest, ClientResponse> handler) {
    ExchangeFunction exchange = request -> Mono.just(handler.apply(request));
    return WebClient.builder().exchangeFunction(exchange).build();
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
    var sender = new RdaBundleSender(CONFIG, client, new DefaultRetryStrategy(meterRegistry));

    create(sender.send(new TransportBundle(new Bundle(), "tid")))
        .expectErrorMatches(
            e ->
                e instanceof TransferProcessException
                    && e.getMessage().equals("RDA polling budget exhausted"))
        .verify();
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
    var sender = new RdaBundleSender(CONFIG, client, new DefaultRetryStrategy(meterRegistry));

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
    var sender = new RdaBundleSender(CONFIG, client, new DefaultRetryStrategy(meterRegistry));

    create(sender.send(new TransportBundle(new Bundle(), "tid")))
        .expectNextCount(1)
        .verifyComplete();
  }

  @Test
  void postOkSkipsPolling() {
    var client = buildClient(request -> ClientResponse.create(HttpStatus.OK).build());
    var sender = new RdaBundleSender(CONFIG, client, new DefaultRetryStrategy(meterRegistry));

    create(sender.send(new TransportBundle(new Bundle(), "tid")))
        .expectNextCount(1)
        .verifyComplete();
  }
}
