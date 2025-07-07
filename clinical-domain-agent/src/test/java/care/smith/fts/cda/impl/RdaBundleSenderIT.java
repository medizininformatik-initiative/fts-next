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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.springframework.http.HttpHeaders.CONTENT_LOCATION;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.cda.BundleSender;
import care.smith.fts.api.cda.BundleSender.Result;
import care.smith.fts.test.connection_scenario.AbstractConnectionScenarioIT;
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
    bundleSender = new RdaBundleSender(config, client, meterRegistry);
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
    wireMock.register(
        rdaRequest()
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

    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(new TransportBundle(bundle, "transferId")))
        .expectNext(new BundleSender.Result())
        .verifyComplete();
  }

  @Test
  void withNumberFormatExceptionInGetRetryAfterWithParsingException() {
    wireMock.register(
        rdaRequest()
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

    var bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(new TransportBundle(bundle, "transferId")))
        .expectNext(new BundleSender.Result())
        .verifyComplete();
  }

  @Test
  void withNumberFormatExceptionInGetRetryAfterWithRetriesExhausted() {
    wireMock.register(
        rdaRequest()
            .willReturn(
                accepted().withHeader(CONTENT_LOCATION, "/api/v2/process/status/processId")));
    wireMock.register(
        get("/api/v2/process/status/processId")
            .willReturn(accepted().withHeader(RETRY_AFTER, "1")));

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
