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
import care.smith.fts.util.WebClientFactory;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest
@WireMockTest
class FhirStoreBundleSenderTest {

  @Autowired MeterRegistry meterRegistry;
  private WebClient client;
  private WireMock wireMock;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntime, @Autowired WebClientFactory clientFactory) {
    client = clientFactory.create(clientConfig(wireMockRuntime));
    wireMock = wireMockRuntime.getWireMock();
  }

  @Test
  void requestErrors() {
    wireMock.register(post(ANY).willReturn(badRequest()));

    var bundleSender = new FhirStoreBundleSender(client, meterRegistry);

    create(bundleSender.send(new Bundle())).expectError().verify();
  }

  @Test
  void bundleSent() {
    wireMock.register(
        post(ANY).withHeader(CONTENT_TYPE, equalTo(APPLICATION_FHIR_JSON_VALUE)).willReturn(ok()));
    var bundleSender = new FhirStoreBundleSender(client, meterRegistry);

    create(bundleSender.send(new Bundle())).expectNext(new BundleSender.Result()).verifyComplete();
  }

  @AfterEach
  void tearDown() {
    wireMock.resetMappings();
  }
}
