package care.smith.fts.cda.impl;

import static care.smith.fts.util.FhirUtils.toBundle;
import static care.smith.fts.util.auth.HttpClientAuthMethod.AuthMethod.NONE;
import static java.util.stream.Stream.generate;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.springframework.http.HttpHeaders.CONTENT_LOCATION;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.OK;
import static reactor.core.publisher.Flux.fromIterable;
import static reactor.core.publisher.Flux.fromStream;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.cda.BundleSender;
import care.smith.fts.test.MockServerUtil;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.error.TransferProcessException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Flux;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
@ExtendWith(MockServerExtension.class)
class RDABundleSenderTest {

  @Autowired MeterRegistry meterRegistry;

  private static final String PATIENT_ID = "patient-102931";
  private static final ConsentedPatient PATIENT = new ConsentedPatient(PATIENT_ID);

  private final HttpClientConfig server = new HttpClientConfig("http://localhost", NONE);
  private final RDABundleSenderConfig config = new RDABundleSenderConfig(server, "example");

  private WebClient client;

  @BeforeEach
  void setUp(MockServerClient mockServer, @Autowired WebClient.Builder builder) {
    var server = MockServerUtil.clientConfig(mockServer);
    client = server.createClient(builder, null);
  }

  @Test
  void nullBundleErrors(MockServerClient mockServer) {
    mockServer.when(request().withMethod("POST")).respond(response().withStatusCode(OK.value()));
    var bundleSender = new RDABundleSender(config, client, meterRegistry);

    create(bundleSender.send(fromStream(generate(() -> null))))
        .expectError(NullPointerException.class)
        .verify();
  }

  @Test
  void badRequest(MockServerClient mockServer) {
    mockServer
        .when(request().withMethod("POST"))
        .respond(response().withStatusCode(BAD_REQUEST.value()));

    var bundleSender = new RDABundleSender(config, client, meterRegistry);

    create(bundleSender.send(Flux.just(new TransportBundle(new Bundle(), "tIDMapName"))))
        .expectError(WebClientResponseException.class)
        .verify();
  }

  @Test
  void contentLocationIsNull(MockServerClient mockServer) {
    mockServer
        .when(request().withMethod("POST"))
        .respond(response().withStatusCode(ACCEPTED.value()));
    mockServer.when(request().withMethod("GET")).respond(response().withStatusCode(OK.value()));

    var bundleSender = new RDABundleSender(config, client, meterRegistry);

    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(fromIterable(List.of(new TransportBundle(bundle, "tIDMapName")))))
        .expectErrorMessage("Missing Content-Location")
        .verify();
  }

  @Test
  void contentLocationIsEmpty(MockServerClient mockServer) {
    mockServer
        .when(request().withMethod("POST"))
        .respond(response().withStatusCode(ACCEPTED.value()).withHeader(CONTENT_LOCATION, ""));
    mockServer.when(request().withMethod("GET")).respond(response().withStatusCode(OK.value()));

    var bundleSender = new RDABundleSender(config, client, meterRegistry);

    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(fromIterable(List.of(new TransportBundle(bundle, "tIDMapName")))))
        .expectErrorMessage("Missing Content-Location")
        .verify();
  }

  @Test
  void bundleSent(MockServerClient mockServer) {
    mockServer
        .when(request().withMethod("POST"))
        .respond(
            response()
                .withStatusCode(ACCEPTED.value())
                .withHeader(CONTENT_LOCATION, "/api/v2/process/status/processId"));
    mockServer
        .when(request().withMethod("GET").withPath("/api/v2/process/status/processId"))
        .respond(response().withStatusCode(OK.value()));

    var bundleSender = new RDABundleSender(config, client, meterRegistry);

    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(fromIterable(List.of(new TransportBundle(bundle, "tIDMapName")))))
        .expectNext(new BundleSender.Result(1))
        .verifyComplete();
  }

  @Test
  void withStatusUnequalAcceptedInWaitForRDACompleted(MockServerClient mockServer) {
    mockServer
        .when(request().withMethod("POST"))
        .respond(
            response()
                .withStatusCode(CREATED.value())
                .withHeader(CONTENT_LOCATION, "/api/v2/process/status/processId"));

    var bundleSender = new RDABundleSender(config, client, meterRegistry);
    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(fromIterable(List.of(new TransportBundle(bundle, "tIDMapName")))))
        .expectErrorMessage("Require ACCEPTED status")
        .verify();
  }

  @Test
  void withNumberFormatExceptionInGetRetryAfter(MockServerClient mockServer) {
    mockServer
        .when(request().withMethod("POST"))
        .respond(
            response()
                .withStatusCode(ACCEPTED.value())
                .withHeader(CONTENT_LOCATION, "/api/v2/process/status/processId"));
    mockServer
        .when(request().withMethod("GET").withPath("/api/v2/process/status/processId"), once())
        .respond(
            response()
                .withStatusCode(ACCEPTED.value())
                .withHeader(RETRY_AFTER, "1")
                .withHeader(CONTENT_LOCATION, "/api/v2/process/status/processId"));
    mockServer
        .when(request().withMethod("GET").withPath("/api/v2/process/status/processId"))
        .respond(response().withStatusCode(OK.value()));

    var bundleSender = new RDABundleSender(config, client, meterRegistry);
    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(fromIterable(List.of(new TransportBundle(bundle, "tIDMapName")))))
        .expectNext(new BundleSender.Result(1))
        .verifyComplete();
  }

  @Test
  void withNumberFormatExceptionInGetRetryAfterWithParsingException(MockServerClient mockServer) {
    mockServer
        .when(request().withMethod("POST"))
        .respond(
            response()
                .withStatusCode(ACCEPTED.value())
                .withHeader(CONTENT_LOCATION, "/api/v2/process/status/processId"));
    mockServer
        .when(request().withMethod("GET").withPath("/api/v2/process/status/processId"), once())
        .respond(
            response()
                .withStatusCode(ACCEPTED.value())
                .withHeader(RETRY_AFTER, "try to parse this!")
                .withHeader(CONTENT_LOCATION, "/api/v2/process/status/processId"));
    mockServer
        .when(request().withMethod("GET").withPath("/api/v2/process/status/processId"))
        .respond(response().withStatusCode(OK.value()));

    var bundleSender = new RDABundleSender(config, client, meterRegistry);
    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(fromIterable(List.of(new TransportBundle(bundle, "tIDMapName")))))
        .expectNext(new BundleSender.Result(1))
        .verifyComplete();
  }

  @Test
  void withNumberFormatExceptionInGetRetryAfterWithRetriesExhausted(MockServerClient mockServer) {
    mockServer
        .when(request().withMethod("POST"))
        .respond(
            response()
                .withStatusCode(ACCEPTED.value())
                .withHeader(CONTENT_LOCATION, "/api/v2/process/status/processId"));
    mockServer
        .when(request().withMethod("GET").withPath("/api/v2/process/status/processId"))
        .respond(response().withStatusCode(ACCEPTED.value()).withHeader(RETRY_AFTER, "1"));

    var bundleSender = new RDABundleSender(config, client, meterRegistry);
    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(fromIterable(List.of(new TransportBundle(bundle, "tIDMapName")))))
        .expectError(TransferProcessException.class)
        .verify();
  }

  @AfterEach
  void tearDown(MockServerClient mockServer) {
    mockServer.reset();
  }
}
