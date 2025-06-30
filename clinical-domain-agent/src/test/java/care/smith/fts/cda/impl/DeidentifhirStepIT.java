package care.smith.fts.cda.impl;

import static care.smith.fts.api.DateShiftPreserve.NONE;
import static care.smith.fts.test.MockServerUtil.clientConfig;
import static care.smith.fts.test.MockServerUtil.jsonResponse;
import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.typesafe.config.ConfigFactory.parseResources;
import static java.time.Duration.ofDays;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.ProblemDetail.forStatusAndDetail;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.api.TransportBundle;
import care.smith.fts.cda.ClinicalDomainAgent;
import care.smith.fts.cda.services.deidentifhir.DeidentifhirUtils;
import care.smith.fts.test.connection_scenario.AbstractConnectionScenarioIT;
import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.tca.TCADomains;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
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
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Mono;

@SpringBootTest(classes = ClinicalDomainAgent.class)
@WireMockTest
class DeidentifhirStepIT extends AbstractConnectionScenarioIT {

  private DeidentifhirStep step;
  private WireMock wireMock;
  private ConsentedPatientBundle consentedPatientBundle;

  @BeforeEach
  void setUp(
      WireMockRuntimeInfo wireMockRuntime,
      @Autowired WebClientFactory clientFactory,
      @Autowired MeterRegistry meterRegistry)
      throws IOException {
    var scrConf = parseResources(DeidentifhirUtils.class, "IDScraper.profile");
    var deiConf = parseResources(DeidentifhirUtils.class, "CDtoTransport.profile");
    var domains = new TCADomains("domain", "domain", "domain");
    var client = clientFactory.create(clientConfig(wireMockRuntime));
    wireMock = wireMockRuntime.getWireMock();
    step = new DeidentifhirStep(client, domains, ofDays(14), NONE, deiConf, scrConf, meterRegistry);

    var bundle = generateOnePatient("id1", "2024", "identifierSystem");
    var consentedPatient = new ConsentedPatient("id1", "system");
    consentedPatientBundle = new ConsentedPatientBundle(bundle, consentedPatient);
  }

  private static MappingBuilder transportMappingRequest() {
    return post("/api/v2/cd/transport-mapping")
        .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE));
  }

  @Override
  protected TestStep<?> createTestStep() {
    return new TestStep<TransportBundle>() {
      @Override
      public MappingBuilder requestBuilder() {
        return DeidentifhirStepIT.transportMappingRequest();
      }

      @Override
      public Mono<TransportBundle> executeStep() {
        return step.deidentify(consentedPatientBundle);
      }

      @Override
      public String acceptedContentType() {
        return MimeTypeUtils.APPLICATION_JSON_VALUE;
      }
    };
  }

  @Test
  void correctRequestSent() {
    wireMock.register(
        transportMappingRequest()
            .withRequestBody(
                equalToJson(
                    """
                    {
                      "patientId": "id1",
                      "resourceIds": [ "id1.identifier.identifierSystem:id1", "id1.Patient:id1" ],
                      "tcaDomains": {
                        "pseudonym": "domain",
                        "salt": "domain",
                        "dateShift": "domain"
                      },
                      "maxDateShift": 1209600.0
                    }
                    """,
                    true,
                    true))
            .willReturn(ok()));

    create(step.deidentify(consentedPatientBundle)).verifyComplete();
  }

  @Test
  void emptyTCAResponseYieldsEmptyResult() {
    wireMock.register(transportMappingRequest().willReturn(ok()));

    create(step.deidentify(consentedPatientBundle)).verifyComplete();
  }

  @Test
  void deidentifySucceeds() {
    wireMock.register(
        transportMappingRequest()
            .willReturn(
                jsonResponse(
                    """
                    {
                      "transferId": "transferId",
                      "transportMapping": { "id1.identifier.identifierSystem:id1": "tident1",
                                            "id1.Patient:id1": "tid1" },
                      "dateShiftValue": 1209600.000000000
                    }
                    """)));

    create(step.deidentify(consentedPatientBundle)).expectNextCount(1).verifyComplete();
  }

  @Test
  void emptyIdsYieldEmptyMono() {
    create(
            step.deidentify(
                new ConsentedPatientBundle(new Bundle(), new ConsentedPatient("id1", "system"))))
        .expectNextCount(0)
        .verifyComplete();
  }

  @Test
  void handleBadRequest() {
    var response = jsonResponse(forStatusAndDetail(BAD_REQUEST, "TCA Returns Bad Request"));
    wireMock.register(transportMappingRequest().willReturn(response));
    create(step.deidentify(consentedPatientBundle))
        .expectErrorMessage("TCA Returns Bad Request")
        .verify();
  }

  @AfterEach
  void tearDown() {
    wireMock.resetMappings();
  }
}
