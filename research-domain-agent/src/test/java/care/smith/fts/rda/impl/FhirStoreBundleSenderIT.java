package care.smith.fts.rda.impl;

import static care.smith.fts.test.MockServerUtil.clientConfig;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;
import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.matching.UrlPattern.ANY;
import static java.util.stream.IntStream.range;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.api.rda.BundleSender.Result;
import care.smith.fts.test.connection_scenario.AbstractConnectionScenarioIT;
import care.smith.fts.util.DefaultRetryStrategy;
import care.smith.fts.util.WebClientFactory;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SpringBootTest
@WireMockTest
class FhirStoreBundleSenderIT extends AbstractConnectionScenarioIT {

  @Autowired MeterRegistry meterRegistry;
  @Autowired WebClientFactory clientFactory;
  private WireMock wireMock;
  private FhirStoreBundleSender bundleSender;
  private WireMockRuntimeInfo wireMockRuntimeInfo;

  @Override
  protected TestStep<?> createTestStep() {
    return new TestStep<Result>() {
      @Override
      public MappingBuilder requestBuilder() {
        return FhirStoreBundleSenderIT.fhirStoreRequest();
      }

      @Override
      public Mono<Result> executeStep() {
        return bundleSender.send(new Bundle());
      }

      @Override
      public Result returnValue() {
        return new Result();
      }
    };
  }

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntime) {
    wireMockRuntimeInfo = wireMockRuntime;
    var client = clientFactory.create(clientConfig(wireMockRuntime));
    var bulkhead =
        Bulkhead.of("test-setup", BulkheadConfig.custom().maxConcurrentCalls(10).build());
    bundleSender =
        new FhirStoreBundleSender(client, new DefaultRetryStrategy(meterRegistry), bulkhead);
    wireMock = wireMockRuntime.getWireMock();
  }

  @Test
  void requestErrors() {
    wireMock.register(post(ANY).willReturn(badRequest()));
    create(bundleSender.send(new Bundle())).expectError().verify();
  }

  @Test
  void bundleSent() {
    var patient = new Patient();
    var bundle = new Bundle();
    bundle.addEntry().setResource(patient);
    wireMock.register(fhirStoreRequest().willReturn(ok()));
    create(bundleSender.send(bundle)).expectNext(new BundleSender.Result()).verifyComplete();
  }

  private static MappingBuilder fhirStoreRequest() {
    return post("/").withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON_VALUE));
  }

  @Test
  void limitsConcurrentRequests() {
    var bulkhead =
        Bulkhead.of(
            "test-limits",
            BulkheadConfig.custom()
                .maxConcurrentCalls(1)
                .maxWaitDuration(Duration.ofSeconds(60))
                .build());
    var client = clientFactory.create(clientConfig(wireMockRuntimeInfo));
    var sender =
        new FhirStoreBundleSender(client, new DefaultRetryStrategy(meterRegistry), bulkhead);

    wireMock.register(
        post("/")
            .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON_VALUE))
            .willReturn(ok().withFixedDelay(500)));

    var sends = range(0, 5).mapToObj(i -> sender.send(new Bundle())).toList();

    StepVerifier.create(Mono.when(sends)).expectComplete().verify(Duration.ofSeconds(10));

    wireMock.verify(5, postRequestedFor(ANY));
  }

  @Test
  void peakConcurrencyNeverExceedsLimit() {
    int maxConcurrent = 2;
    var bulkhead =
        Bulkhead.of(
            "test-peak",
            BulkheadConfig.custom()
                .maxConcurrentCalls(maxConcurrent)
                .maxWaitDuration(Duration.ofSeconds(60))
                .build());
    var client = clientFactory.create(clientConfig(wireMockRuntimeInfo));
    var sender =
        new FhirStoreBundleSender(client, new DefaultRetryStrategy(meterRegistry), bulkhead);

    wireMock.register(
        post("/")
            .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON_VALUE))
            .willReturn(ok().withFixedDelay(500)));

    var peakInFlight = new AtomicInteger();
    bulkhead
        .getEventPublisher()
        .onCallPermitted(
            e -> {
              var inFlight = maxConcurrent - bulkhead.getMetrics().getAvailableConcurrentCalls();
              peakInFlight.updateAndGet(p -> Math.max(p, inFlight));
            });

    var sends = range(0, 6).mapToObj(i -> sender.send(new Bundle())).toList();

    StepVerifier.create(Mono.when(sends)).expectComplete().verify(Duration.ofSeconds(30));

    assertThat(peakInFlight.get()).isEqualTo(maxConcurrent);
    assertThat(bulkhead.getMetrics().getAvailableConcurrentCalls()).isGreaterThanOrEqualTo(0);
  }

  @Test
  void absorbContractExcessWaitsInsteadOfRefusing() {
    int maxConcurrent = 2;
    var bulkhead =
        Bulkhead.of(
            "test-absorb",
            BulkheadConfig.custom()
                .maxConcurrentCalls(maxConcurrent)
                .maxWaitDuration(Duration.ofSeconds(60))
                .build());
    var client = clientFactory.create(clientConfig(wireMockRuntimeInfo));
    var sender =
        new FhirStoreBundleSender(client, new DefaultRetryStrategy(meterRegistry), bulkhead);

    wireMock.register(
        post("/")
            .withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON_VALUE))
            .willReturn(ok().withFixedDelay(200)));

    var sends = range(0, 6).mapToObj(i -> sender.send(new Bundle())).toList();

    StepVerifier.create(Mono.when(sends)).expectComplete().verify(Duration.ofSeconds(30));

    wireMock.verify(6, postRequestedFor(ANY));
  }

  @AfterEach
  void tearDown() {
    wireMock.resetMappings();
  }
}
