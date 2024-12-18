package care.smith.fts.cda.impl;

import static care.smith.fts.test.MockServerUtil.clientConfig;
import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.typesafe.config.ConfigFactory.parseResources;
import static java.time.Duration.ofDays;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.cda.ClinicalDomainAgent;
import care.smith.fts.cda.services.deidentifhir.DeidentifhirUtils;
import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.tca.TCADomains;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = ClinicalDomainAgent.class)
@WireMockTest
class DeidentifhirStepTest {

  @Autowired MeterRegistry meterRegistry;
  private DeidentifhirStep step;
  private WireMock wireMock;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntime, @Autowired WebClientFactory clientFactory) {
    var scraperConfig = parseResources(DeidentifhirUtils.class, "IDScraper.profile");
    var deidentifhirConfig = parseResources(DeidentifhirUtils.class, "CDtoTransport.profile");
    var domains = new TCADomains("domain", "domain", "domain");
    var client = clientFactory.create(clientConfig(wireMockRuntime));
    wireMock = wireMockRuntime.getWireMock();
    step =
        new DeidentifhirStep(
            client, domains, ofDays(14), deidentifhirConfig, scraperConfig, meterRegistry);
  }

  @Test
  void correctRequestSent() throws IOException {
    wireMock.register(
        post("/api/v2/cd/transport-mapping")
            .withRequestBody(
                equalToJson(
                    """
                                {
                                  "patientId" : "id1",
                                  "resourceIds" : [ "id1.identifier.identifierSystem:id1", "id1.Patient:id1" ],
                                  "tcaDomains": {
                                    "pseudonym" : "domain",
                                    "salt" : "domain",
                                    "dateShift" : "domain"
                                  },
                                  "maxDateShift" : 1209600.0
                                }
                                """,
                    true,
                    true))
            .willReturn(ok()));

    var consentedPatient = new ConsentedPatient("id1");
    var bundle = generateOnePatient("id1", "2024", "identifierSystem");
    var consentedPatientBundle = new ConsentedPatientBundle(bundle, consentedPatient);

    create(step.deidentify(consentedPatientBundle)).verifyComplete();
  }

  @Test
  void emptyTCAResponseYieldsEmptyResult() throws IOException {
    wireMock.register(post("/api/v2/cd/transport-mapping").willReturn(ok()));

    var consentedPatient = new ConsentedPatient("id1");
    var bundle = generateOnePatient("id1", "2024", "identifierSystem");
    var consentedPatientBundle = new ConsentedPatientBundle(bundle, consentedPatient);

    create(step.deidentify(consentedPatientBundle)).verifyComplete();
  }

  @Test
  void deidentifySucceeds() throws IOException {
    wireMock.register(
        post("/api/v2/cd/transport-mapping")
            .willReturn(
                jsonResponse(
                    """
                            {
                              "transferId": "transferId",
                              "transportMapping": { "id1.identifier.identifierSystem:id1": "tident1",
                                                    "id1.Patient:id1": "tid1" },
                              "dateShiftValue": 1209600.000000000
                            }
                            """,
                    200)));

    var consentedPatient = new ConsentedPatient("id1");
    var bundle = generateOnePatient("id1", "2024", "identifierSystem");
    var consentedPatientBundle = new ConsentedPatientBundle(bundle, consentedPatient);

    create(step.deidentify(consentedPatientBundle)).expectNextCount(1).verifyComplete();
  }

  @Test
  void emptyIdsYieldEmptyMono() {
    create(step.deidentify(new ConsentedPatientBundle(new Bundle(), new ConsentedPatient("id1"))))
        .expectNextCount(0)
        .verifyComplete();
  }

  @AfterEach
  void tearDown() {
    wireMock.resetMappings();
  }
}
