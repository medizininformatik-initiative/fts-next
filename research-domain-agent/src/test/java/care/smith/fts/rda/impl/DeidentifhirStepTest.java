package care.smith.fts.rda.impl;

import static care.smith.fts.test.MockServerUtil.clientConfig;
import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.typesafe.config.ConfigFactory.parseResources;
import static org.assertj.core.api.Assertions.assertThat;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.rda.services.deidentifhir.DeidentifhirUtil;
import care.smith.fts.util.WebClientFactory;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@WireMockTest
class DeidentifhirStepTest {
  @Autowired MeterRegistry meterRegistry;
  private DeidentifhirStep step;
  private WireMock wireMock;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntime, @Autowired WebClientFactory clientFactory) {
    var config = parseResources(DeidentifhirUtil.class, "TransportToRD.profile");
    var client = clientFactory.create(clientConfig(wireMockRuntime));
    step = new DeidentifhirStep(config, client, meterRegistry);
    wireMock = wireMockRuntime.getWireMock();
  }

  @AfterEach
  void tearDown() {
    wireMock.resetMappings();
  }

  @Test
  void correctRequestSent() throws IOException {
    wireMock.register(
        WireMock.post(urlPathEqualTo("/api/v2/rd/research-mapping"))
            .withRequestBody(equalTo("transferId"))
            .willReturn(ok()));

    var bundle = generateOnePatient("tid1", "2024", "identifierSystem");

    create(step.deidentify(new TransportBundle(bundle, "transferId"))).verifyComplete();
  }

  @Test
  void emptyTCAResponseYieldsEmptyResult() throws IOException {
    wireMock.register(
        WireMock.post(urlPathEqualTo("/api/v2/rd/research-mapping")).willReturn(ok()));

    var bundle = generateOnePatient("tid1", "2024", "identifierSystem");

    create(step.deidentify(new TransportBundle(bundle, "transferId"))).verifyComplete();
  }

  @Test
  void deidentifySucceeds() throws IOException {
    wireMock.register(
        WireMock.post(urlPathEqualTo("/api/v2/rd/research-mapping"))
            .withRequestBody(equalTo("transferId"))
            .willReturn(
                jsonResponse(
                    """
                          {"tidPidMap": {"tid1": "pid1"},
                           "dateShiftBy": "P12D"}
                          """,
                    200)));

    var bundle = generateOnePatient("tid1", "2024", "identifierSystem");

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
