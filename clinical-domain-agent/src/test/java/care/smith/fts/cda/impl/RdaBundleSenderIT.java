package care.smith.fts.cda.impl;

import static care.smith.fts.test.MockServerUtil.FIRST;
import static care.smith.fts.test.MockServerUtil.REST;
import static care.smith.fts.test.MockServerUtil.accepted;
import static care.smith.fts.test.MockServerUtil.clientConfig;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;
import static care.smith.fts.util.fhir.FhirUtils.toBundle;
import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.matching.UrlPattern.ANY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.http.HttpHeaders.CONTENT_LOCATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.cda.BundleSender;
import care.smith.fts.api.cda.BundleSender.Result;
import care.smith.fts.test.connection_scenario.AbstractConnectionScenarioIT;
import care.smith.fts.util.BackpressureRetryStrategy;
import care.smith.fts.util.DefaultRetryStrategy;
import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.error.TransferProcessException;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Mono;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
@WireMockTest
class RdaBundleSenderIT extends AbstractConnectionScenarioIT {

  private static final String PATIENT_ID = "patient-102931";

  @Autowired MeterRegistry meterRegistry;
  private WireMock wireMock;

  private RdaBundleSender bundleSender;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntime, @Autowired WebClientFactory clientFactory) {
    var server = clientConfig(wireMockRuntime);
    var client = clientFactory.create(server);
    wireMock = wireMockRuntime.getWireMock();

    var config = new RdaBundleSenderConfig(server, "example");
    bundleSender =
        new RdaBundleSender(
            config,
            client,
            new BackpressureRetryStrategy(meterRegistry, new DefaultRetryStrategy(meterRegistry)));
  }

  private static MappingBuilder rdaRequest() {
    return post("/api/v2/process/example/patient")
        .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON_VALUE));
  }

  @Override
  protected TestStep<?> createTestStep() {
    return new TestStep<Result>() {
      @Override
      public MappingBuilder requestBuilder() {
        return RdaBundleSenderIT.rdaRequest();
      }

      @Override
      public Mono<Result> executeStep() {
        return bundleSender.send(new TransportBundle(new Bundle(), "transferId"));
      }

      @Override
      public Result returnValue() {
        return new Result();
      }
    };
  }

  @Test
  void nullBundleErrors() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> bundleSender.send(null).subscribe());
  }

  @Test
  void badRequest() {
    wireMock.register(rdaRequest().willReturn(WireMock.badRequest()));

    create(bundleSender.send(new TransportBundle(new Bundle(), "transferId")))
        .expectError(WebClientResponseException.class)
        .verify();
  }

  @Test
  void contentLocationIsNull() {
    wireMock.register(rdaRequest().willReturn(accepted()));
    wireMock.register(get(ANY).willReturn(ok())); // get status

    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(new TransportBundle(bundle, "transferId")))
        .expectErrorMessage("Missing Content-Location")
        .verify();
  }

  @Test
  void contentLocationIsEmpty() {
    wireMock.register(rdaRequest().willReturn(accepted().withHeader(CONTENT_LOCATION, "")));

    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(new TransportBundle(bundle, "transferId")))
        .expectErrorMessage("Missing Content-Location")
        .verify();
  }

  @Test
  void bundleSent() {
    wireMock.register(
        rdaRequest()
            .willReturn(
                accepted().withHeader(CONTENT_LOCATION, "/api/v2/process/status/processId")));

    wireMock.register(get("/api/v2/process/status/processId").willReturn(ok()));

    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(new TransportBundle(bundle, "transferId")))
        .expectNext(new BundleSender.Result())
        .verifyComplete();
  }

  @Test
  void withStatusUnequalAcceptedInWaitForRDACompleted() {
    wireMock.register(
        rdaRequest()
            .willReturn(
                created().withHeader(CONTENT_LOCATION, "/api/v2/process/status/processId")));

    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(new TransportBundle(bundle, "transferId")))
        .expectErrorMessage("Require ACCEPTED status")
        .verify();
  }

  @Test
  void withRetryAfterOnFirstAttempt() {
    assertThat(timeSendWithDeferredStatus("RetryAfterAtFirst", "1"))
        .isGreaterThanOrEqualTo(Duration.ofSeconds(1));
  }

  @Test
  void withNumberFormatExceptionInGetRetryAfterWithParsingException() {
    // An unparsable header falls back to 1s, so the send cannot finish sooner than that.
    assertThat(timeSendWithDeferredStatus("BadRetryAfterAtFirst", "try to parse this!"))
        .isGreaterThanOrEqualTo(Duration.ofSeconds(1));
  }

  /**
   * Registers a status endpoint that answers ACCEPTED once, carrying {@code retryAfter} as its
   * Retry-After header, and OK on every later poll.
   */
  private void registerStatusAcceptedThenOk(String scenario, String retryAfter) {
    wireMock.register(
        get("/api/v2/process/status/processId")
            .inScenario(scenario)
            .whenScenarioStateIs(FIRST)
            .willReturn(
                accepted()
                    .withHeader(CONTENT_LOCATION, "/api/v2/process/status/processId")
                    .withHeader(RETRY_AFTER, retryAfter))
            .willSetStateTo(REST));

    wireMock.register(
        get("/api/v2/process/status/processId")
            .inScenario(scenario)
            .whenScenarioStateIs(REST)
            .willReturn(ok()));
  }

  /**
   * Sends a bundle against a status endpoint that defers once, and returns how long the send took.
   * The sender delays the poll result by the Retry-After the RDA asked for, so the elapsed time of
   * the whole send is the observable proof that the backpressure was honoured.
   */
  private Duration timeSendWithDeferredStatus(String scenario, String retryAfter) {
    wireMock.register(
        rdaRequest()
            .willReturn(
                accepted().withHeader(CONTENT_LOCATION, "/api/v2/process/status/processId")));
    registerStatusAcceptedThenOk(scenario, retryAfter);

    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    return create(bundleSender.send(new TransportBundle(bundle, "transferId")))
        .expectNext(new BundleSender.Result())
        .verifyComplete();
  }

  @Test
  void waitsTheRetryAfterTheRdaAsksFor() {
    // The RDA asks for 2s, which is longer than the 1s fallback. A sender that ignores the header
    // finishes in well under 2s and fails here.
    assertThat(timeSendWithDeferredStatus("RetryAfterTwoSeconds", "2"))
        .isGreaterThanOrEqualTo(Duration.ofSeconds(2));
  }

  @Test
  void pollingBudgetExhaustedWithStatusStillAccepted() {
    wireMock.register(
        rdaRequest()
            .willReturn(
                accepted().withHeader(CONTENT_LOCATION, "/api/v2/process/status/processId")));
    wireMock.register(
        get("/api/v2/process/status/processId")
            .willReturn(accepted().withHeader(RETRY_AFTER, "1")));

    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(new TransportBundle(bundle, "transferId")))
        .expectErrorMessage("RDA polling budget exhausted")
        .verify();
  }

  @Test
  void unexpectedStatusFromPollingEndpoint() {
    wireMock.register(
        rdaRequest()
            .willReturn(
                accepted().withHeader(CONTENT_LOCATION, "/api/v2/process/status/processId")));
    wireMock.register(get("/api/v2/process/status/processId").willReturn(created()));

    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(new TransportBundle(bundle, "transferId")))
        .expectErrorMatches(
            e ->
                e instanceof TransferProcessException
                    && e.getMessage().startsWith("Unexpected RDA status: 201"))
        .verify();
  }

  @Test
  void retriesOn429ThenSucceeds() {
    wireMock.register(
        rdaRequest()
            .inScenario("429ThenAccept")
            .whenScenarioStateIs(FIRST)
            .willReturn(WireMock.status(429).withHeader(RETRY_AFTER, "1"))
            .willSetStateTo(REST));

    wireMock.register(
        rdaRequest()
            .inScenario("429ThenAccept")
            .whenScenarioStateIs(REST)
            .willReturn(
                accepted().withHeader(CONTENT_LOCATION, "/api/v2/process/status/processId")));

    wireMock.register(get("/api/v2/process/status/processId").willReturn(ok()));

    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(new TransportBundle(bundle, "transferId")))
        .expectNext(new BundleSender.Result())
        .verifyComplete();
  }

  @Test
  void exhaustsRetriesOnPersistent429() {
    wireMock.register(rdaRequest().willReturn(WireMock.status(429).withHeader(RETRY_AFTER, "1")));

    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(new TransportBundle(bundle, "transferId"))).expectError().verify();
  }

  @AfterEach
  void tearDown() {
    wireMock.resetMappings();
  }
}
