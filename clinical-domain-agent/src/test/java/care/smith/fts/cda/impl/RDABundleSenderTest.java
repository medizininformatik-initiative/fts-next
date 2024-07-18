package care.smith.fts.cda.impl;

import static care.smith.fts.test.WebClientTestUtil.matchRequest;
import static care.smith.fts.util.FhirUtils.toBundle;
import static care.smith.fts.util.auth.HTTPClientAuthMethod.AuthMethod.NONE;
import static java.util.stream.Stream.generate;
import static org.springframework.http.HttpHeaders.CONTENT_LOCATION;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.web.reactive.function.client.WebClient.builder;
import static reactor.core.publisher.Flux.fromIterable;
import static reactor.core.publisher.Flux.fromStream;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.cda.BundleSender;
import care.smith.fts.test.WebClientTestUtil;
import care.smith.fts.util.HTTPClientConfig;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.*;

@ExtendWith(MockitoExtension.class)
class RDABundleSenderTest {

  private static final String PATIENT_ID = "patient-102931";
  private static final ConsentedPatient PATIENT = new ConsentedPatient(PATIENT_ID);

  private final HTTPClientConfig server = new HTTPClientConfig("http://localhost", NONE);
  private final RDABundleSenderConfig config = new RDABundleSenderConfig(server, "example");

  @Test
  void nullBundleErrors() {
    var client =
        builder()
            .exchangeFunction(
                matchRequest(HttpMethod.POST).willRespond(ClientResponse.create(OK).build()));
    client.exchangeStrategies(ExchangeStrategies.builder().build());
    var bundleSender = new RDABundleSender(config, config.server().createClient(client));

    create(bundleSender.send(fromStream(generate(() -> null))))
        .expectError(NullPointerException.class)
        .verify();
  }

  @Test
  void requestErrors() {
    var client =
        builder()
            .exchangeFunction(
                matchRequest(HttpMethod.POST)
                    .willRespond(ClientResponse.create(BAD_REQUEST).build()));
    var bundleSender = new RDABundleSender(config, config.server().createClient(client));

    create(bundleSender.send(fromIterable(List.of(new TransportBundle(new Bundle(), Set.of())))))
        .expectError()
        .verify();
  }

  @Test
  void missingContentLocation() {
    var client =
        builder()
            .exchangeFunction(
                WebClientTestUtil.requestsInOrder(
                    matchRequest(HttpMethod.POST)
                        .willRespond(ClientResponse.create(ACCEPTED).build()),
                    matchRequest(HttpMethod.GET).willRespond(ClientResponse.create(OK).build())));
    var bundleSender = new RDABundleSender(config, config.server().createClient(client));

    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(fromIterable(List.of(new TransportBundle(bundle, Set.of())))))
        .expectErrorMessage("Missing Content-Location")
        .verify();
  }

  @Test
  void bundleSent() {
    var client =
        builder()
            .exchangeFunction(
                WebClientTestUtil.requestsInOrder(
                    matchRequest(HttpMethod.POST)
                        .willRespond(
                            ClientResponse.create(ACCEPTED)
                                .header(
                                    CONTENT_LOCATION,
                                    "http://localhost:1234/api/v2/process/status/processId")
                                .build()),
                    matchRequest(HttpMethod.GET).willRespond(ClientResponse.create(OK).build())));
    var bundleSender = new RDABundleSender(config, config.server().createClient(client));

    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(fromIterable(List.of(new TransportBundle(bundle, Set.of())))))
        .expectNext(new BundleSender.Result(1))
        .verifyComplete();
  }

  @Test
  void withNumberFormatExceptionInGetRetryAfter() {
    var client =
        builder()
            .exchangeFunction(
                WebClientTestUtil.requestsInOrder(
                    matchRequest(HttpMethod.POST)
                        .willRespond(
                            ClientResponse.create(ACCEPTED)
                                .header(
                                    CONTENT_LOCATION,
                                    "http://localhost:1234/api/v2/process/status/processId")
                                .build()),
                    matchRequest(HttpMethod.GET)
                        .willRespond(
                            ClientResponse.create(ACCEPTED)
                                .header(RETRY_AFTER, "Hello there")
                                .build()),
                    matchRequest(HttpMethod.GET).willRespond(ClientResponse.create(OK).build())));
    var bundleSender = new RDABundleSender(config, config.server().createClient(client));

    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(fromIterable(List.of(new TransportBundle(bundle, Set.of())))))
        .expectNext(new BundleSender.Result(1))
        .verifyComplete();
  }
}
