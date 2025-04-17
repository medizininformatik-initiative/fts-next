package care.smith.fts.rda.impl;

import static care.smith.fts.test.MockServerUtil.APPLICATION_FHIR_JSON;
import static care.smith.fts.test.MockServerUtil.clientConfig;
import static care.smith.fts.test.MockServerUtil.jsonResponse;
import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.typesafe.config.ConfigFactory.parseResources;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.rda.services.deidentifhir.DeidentifhirUtil;
import care.smith.fts.test.connection_scenario.AbstractConnectionScenarioIT;
import care.smith.fts.util.WebClientFactory;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Mono;

@SpringBootTest
@WireMockTest
class DeidentifhirStepIT extends AbstractConnectionScenarioIT {

  @Autowired MeterRegistry meterRegistry;
  private WireMock wireMock;

  private DeidentifhirStep step;
  private Bundle bundle;

  @Override
  protected Stream<TestStep<?>> createTestSteps() {
    return Stream.of(
        new TestStep<Bundle>() {
          @Override
          public MappingBuilder requestBuilder() {
            return DeidentifhirStepIT.researchMappingRequest();
          }

          @Override
          public Mono<Bundle> executeStep() {
            return step.deidentify(new TransportBundle(bundle, "transferId"));
          }

          @Override
          public String acceptedContentType() {
            return APPLICATION_FHIR_JSON;
          }
        });
  }

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntime, @Autowired WebClientFactory clientFactory)
      throws IOException {
    var config = parseResources(DeidentifhirUtil.class, "TransportToRD.profile");
    var client = clientFactory.create(clientConfig(wireMockRuntime));
    step = new DeidentifhirStep(config, client, meterRegistry);
    wireMock = wireMockRuntime.getWireMock();
    bundle = generateOnePatient("tid1", "2024", "identifierSystem");
  }

  @AfterEach
  void tearDown() {
    wireMock.resetMappings();
  }

  private static MappingBuilder researchMappingRequest() {
    return post("/api/v2/rd/research-mapping")
        .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE));
  }

  @Test
  void correctRequestSent() {
    wireMock.register(
        WireMock.post(urlPathEqualTo("/api/v2/rd/research-mapping"))
            .withRequestBody(equalTo("transferId"))
            .willReturn(ok()));

    create(step.deidentify(new TransportBundle(bundle, "transferId"))).verifyComplete();
  }

  @Test
  void emptyTCAResponseYieldsEmptyResult() {
    wireMock.register(
        WireMock.post(urlPathEqualTo("/api/v2/rd/research-mapping")).willReturn(ok()));

    create(step.deidentify(new TransportBundle(bundle, "transferId"))).verifyComplete();
  }

  @Test
  void deidentifySucceeds() {
    wireMock.register(
        WireMock.post(urlPathEqualTo("/api/v2/rd/research-mapping"))
            .withRequestBody(equalTo("transferId"))
            .willReturn(
                jsonResponse(
                    """
                    {
                      "tidPidMap": {"tid1": "pid1"},
                      "dateShiftBy": "P12D"
                    }
                    """)));

    create(step.deidentify(new TransportBundle(bundle, "transferId")))
        .assertNext(
            b -> {
              assertThat(b.getEntry().size()).isEqualTo(1);
              assertThat(b.getEntryFirstRep()).isNotNull();

              Bundle innerBundle = (Bundle) b.getEntryFirstRep().getResource();
              Resource resource = innerBundle.getEntryFirstRep().getResource();
              assertThat(resource.getIdPart()).isEqualTo("pid1");
            })
        .verifyComplete();
  }
}
