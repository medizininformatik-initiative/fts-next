package care.smith.fts.cda.impl;

import static care.smith.fts.test.MockServerUtil.FIRST;
import static care.smith.fts.test.MockServerUtil.REST;
import static care.smith.fts.test.MockServerUtil.accepted;
import static care.smith.fts.test.MockServerUtil.clientConfig;
import static care.smith.fts.util.FhirUtils.toBundle;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;
import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.matching.UrlPattern.ANY;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.http.HttpHeaders.CONTENT_LOCATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.cda.BundleSender;
import care.smith.fts.api.cda.BundleSender.Result;
import care.smith.fts.test.connection_scenario.AbstractConnectionScenarioIT;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.error.TransferProcessException;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
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
class RDABundleSenderIT extends AbstractConnectionScenarioIT {

  @Autowired MeterRegistry meterRegistry;

  private static final String PATIENT_ID = "patient-102931";
  private final HttpClientConfig server = new HttpClientConfig("http://localhost");
  private final RDABundleSenderConfig config = new RDABundleSenderConfig(server, "example");

  private WebClient client;
  private WireMock wireMock;

  private static RDABundleSender bundleSender;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntime, @Autowired WebClientFactory clientFactory) {
    client = clientFactory.create(clientConfig(wireMockRuntime));
    bundleSender = new RDABundleSender(config, client, meterRegistry);
    wireMock = wireMockRuntime.getWireMock();
  }

  private static MappingBuilder getBuilder() {
    return post("/api/v2/process/example/patient")
        .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON_VALUE));
  }

  @Override
  protected Stream<TestStep<?>> createTestSteps() {
    return Stream.of(
        new TestStep<Result>() {
          @Override
          public MappingBuilder getBuilder() {
            return RDABundleSenderIT.getBuilder();
          }

          @Override
          public Mono<Result> executeStep() {
            return RDABundleSenderIT.bundleSender.send(
                new TransportBundle(new Bundle(), "transferId"));
          }

          @Override
          public Result returnValue() {
            return new Result();
          }
        });
  }

  @Test
  void nullBundleErrors() {
    var bundleSender = new RDABundleSender(config, client, meterRegistry);

    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> bundleSender.send(null).subscribe());
  }

  @Test
  void badRequest() {
    wireMock.register(getBuilder().willReturn(WireMock.badRequest()));

    var bundleSender = new RDABundleSender(config, client, meterRegistry);

    create(bundleSender.send(new TransportBundle(new Bundle(), "transferId")))
        .expectError(WebClientResponseException.class)
        .verify();
  }

  @Test
  void contentLocationIsNull() {
    wireMock.register(getBuilder().willReturn(accepted()));
    wireMock.register(get(ANY).willReturn(ok())); // get status

    var bundleSender = new RDABundleSender(config, client, meterRegistry);

    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(new TransportBundle(bundle, "transferId")))
        .expectErrorMessage("Missing Content-Location")
        .verify();
  }

  @Test
  void contentLocationIsEmpty() {
    wireMock.register(getBuilder().willReturn(accepted().withHeader(CONTENT_LOCATION, "")));

    var bundleSender = new RDABundleSender(config, client, meterRegistry);

    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(new TransportBundle(bundle, "transferId")))
        .expectErrorMessage("Missing Content-Location")
        .verify();
  }

  @Test
  void bundleSent() {
    wireMock.register(
        getBuilder()
            .willReturn(
                accepted().withHeader(CONTENT_LOCATION, "/api/v2/process/status/processId")));

    wireMock.register(get("/api/v2/process/status/processId").willReturn(ok()));

    var bundleSender = new RDABundleSender(config, client, meterRegistry);

    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(new TransportBundle(bundle, "transferId")))
        .expectNext(new BundleSender.Result())
        .verifyComplete();
  }

  @Test
  void withStatusUnequalAcceptedInWaitForRDACompleted() {
    wireMock.register(
        getBuilder()
            .willReturn(
                created().withHeader(CONTENT_LOCATION, "/api/v2/process/status/processId")));

    var bundleSender = new RDABundleSender(config, client, meterRegistry);
    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(new TransportBundle(bundle, "transferId")))
        .expectErrorMessage("Require ACCEPTED status")
        .verify();
  }

  @Test
  void withRetryAfterOnFirstAttempt() {
    wireMock.register(
        getBuilder()
            .willReturn(
                accepted().withHeader(CONTENT_LOCATION, "/api/v2/process/status/processId")));

    wireMock.register(
        get("/api/v2/process/status/processId")
            .inScenario("BadRetryAfterAtFirst")
            .whenScenarioStateIs(FIRST)
            .willReturn(
                accepted()
                    .withHeader(RETRY_AFTER, "1")
                    .withHeader(CONTENT_LOCATION, "/api/v2/process/status/processId"))
            .willSetStateTo(REST));

    wireMock.register(
        get("/api/v2/process/status/processId")
            .inScenario("BadRetryAfterAtFirst")
            .whenScenarioStateIs(REST)
            .willReturn(ok()));

    var bundleSender = new RDABundleSender(config, client, meterRegistry);
    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(new TransportBundle(bundle, "transferId")))
        .expectNext(new BundleSender.Result())
        .verifyComplete();
  }

  @Test
  void withNumberFormatExceptionInGetRetryAfterWithParsingException() {
    wireMock.register(
        getBuilder()
            .willReturn(
                accepted().withHeader(CONTENT_LOCATION, "/api/v2/process/status/processId")));

    wireMock.register(
        get("/api/v2/process/status/processId")
            .inScenario("BadRetryAfterAtFirst")
            .whenScenarioStateIs(FIRST)
            .willReturn(
                accepted()
                    .withHeader(RETRY_AFTER, "try to parse this!")
                    .withHeader(CONTENT_LOCATION, "/api/v2/process/status/processId"))
            .willSetStateTo(REST));

    wireMock.register(
        get("/api/v2/process/status/processId")
            .inScenario("BadRetryAfterAtFirst")
            .whenScenarioStateIs(REST)
            .willReturn(ok()));

    var bundleSender = new RDABundleSender(config, client, meterRegistry);
    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(new TransportBundle(bundle, "transferId")))
        .expectNext(new BundleSender.Result())
        .verifyComplete();
  }

  @Test
  void withNumberFormatExceptionInGetRetryAfterWithRetriesExhausted() {
    wireMock.register(
        getBuilder()
            .willReturn(
                accepted().withHeader(CONTENT_LOCATION, "/api/v2/process/status/processId")));
    wireMock.register(
        get("/api/v2/process/status/processId")
            .willReturn(accepted().withHeader(RETRY_AFTER, "1")));

    var bundleSender = new RDABundleSender(config, client, meterRegistry);
    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(new TransportBundle(bundle, "transferId")))
        .expectError(TransferProcessException.class)
        .verify();
  }

  @AfterEach
  void tearDown() {
    wireMock.resetMappings();
  }
}
