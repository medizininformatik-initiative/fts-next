package care.smith.fts.rda.impl;

import static care.smith.fts.test.MockServerUtil.clientConfig;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;
import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.matching.UrlPattern.ANY;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.api.rda.BundleSender.Result;
import care.smith.fts.test.connection_scenario.AbstractConnectionScenarioIT;
import care.smith.fts.util.WebClientFactory;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Mono;

@SpringBootTest
@WireMockTest
class FhirStoreBundleSenderIT extends AbstractConnectionScenarioIT {

  @Autowired MeterRegistry meterRegistry;
  private WireMock wireMock;
  private static FhirStoreBundleSender bundleSender;

  @Override
  protected TestStep<?> createTestStep() {
    return new TestStep<Result>() {
      @Override
      public MappingBuilder requestBuilder() {
        return FhirStoreBundleSenderIT.fhirStoreRequest();
      }

      @Override
      public Mono<Result> executeStep() {
        return FhirStoreBundleSenderIT.bundleSender.send(new Bundle());
      }

      @Override
      public Result returnValue() {
        return new Result();
      }
    };
  }

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntime, @Autowired WebClientFactory clientFactory) {
    var client = clientFactory.create(clientConfig(wireMockRuntime));
    bundleSender = new FhirStoreBundleSender(client, meterRegistry);
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

  @AfterEach
  void tearDown() {
    wireMock.resetMappings();
  }
}
