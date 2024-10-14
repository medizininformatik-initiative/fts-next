package care.smith.fts.cda.impl;

import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static com.typesafe.config.ConfigFactory.parseResources;
import static java.time.Duration.ofDays;
import static org.mockserver.matchers.MatchType.ONLY_MATCHING_FIELDS;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.cda.services.deidentifhir.DeidentifhirUtils;
import care.smith.fts.test.MockServerUtil;
import care.smith.fts.util.tca.TCADomains;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest
@ExtendWith(MockServerExtension.class)
class DeidentifhirStepTest {

  @Autowired MeterRegistry meterRegistry;
  private DeidentifhirStep step;

  @BeforeEach
  void setUp(MockServerClient mockServer) {
    var scraperConfig = parseResources(DeidentifhirUtils.class, "IDScraper.profile");
    var deidentifhirConfig = parseResources(DeidentifhirUtils.class, "CDtoTransport.profile");
    var server = MockServerUtil.clientConfig(mockServer);
    var domains = new TCADomains("domain", "domain", "domain");
    var client = server.createClient(WebClient.builder(), null);
    step =
        new DeidentifhirStep(
            client, domains, ofDays(14), deidentifhirConfig, scraperConfig, meterRegistry);
  }

  @Test
  void correctRequestSent(MockServerClient mockServer) throws IOException {
    mockServer
        .when(
            request()
                .withMethod("POST")
                .withPath("/api/v2/cd/transport-mapping")
                .withBody(
                    json(
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
                        ONLY_MATCHING_FIELDS)))
        .respond(response().withStatusCode(200));

    var consentedPatient = new ConsentedPatient("id1");
    var bundle = generateOnePatient("id1", "2024", "identifierSystem");
    var consentedPatientBundle = new ConsentedPatientBundle(bundle, consentedPatient);

    create(step.deidentify(consentedPatientBundle)).verifyComplete();
  }

  @Test
  void emptyTCAResponseYieldsEmptyResult(MockServerClient mockServer) throws IOException {
    mockServer
        .when(request().withMethod("POST").withPath("/api/v2/cd/transport-mapping"))
        .respond(response().withStatusCode(200));

    var consentedPatient = new ConsentedPatient("id1");
    var bundle = generateOnePatient("id1", "2024", "identifierSystem");
    var consentedPatientBundle = new ConsentedPatientBundle(bundle, consentedPatient);

    create(step.deidentify(consentedPatientBundle)).verifyComplete();
  }

  @Test
  void deidentifySucceeds(MockServerClient mockServer) throws IOException {
    mockServer
        .when(request().withMethod("POST").withPath("/api/v2/cd/transport-mapping"))
        .respond(
            response()
                .withBody(
                    json(
                        """
                            {
                              "resarchMappingName": "resarchMappingName",
                              "transportMapping": { "id1.identifier.identifierSystem:id1": "tident1",
                                                    "id1.Patient:id1": "tid1" },
                              "dateShiftValue": 1209600.000000000
                            }
                            """))
                .withStatusCode(200));

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
  void tearDown(MockServerClient mockServer) {
    mockServer.reset();
  }
}
