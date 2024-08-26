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
import care.smith.fts.cda.services.deidentifhir.DeidentifhirUtil;
import care.smith.fts.test.MockServerUtil;
import com.typesafe.config.Config;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@ExtendWith(MockServerExtension.class)
class DeidentifhirStepTest {

  private DeidentifhirStep step;

  @BeforeEach
  void setUp(MockServerClient mockServer) {
    Config scraperConfig = parseResources(DeidentifhirUtil.class, "IDScraper.profile");
    Config deidentifhirConfig = parseResources(DeidentifhirUtil.class, "CDtoTransport.profile");
    var server = MockServerUtil.clientConfig(mockServer);

    step =
        new DeidentifhirStep(
            server.createClient(WebClient.builder()),
            "domain",
            ofDays(14),
            deidentifhirConfig,
            scraperConfig);
  }

  @Test
  void correctRequestSent(MockServerClient mockServer) throws IOException {
    mockServer
        .when(
            request()
                .withMethod("POST")
                .withPath("/api/v2/cd/transport-ids-and-date-shifting-values")
                .withBody(
                    json(
                        """
                                {
                                  "patientId" : "id1.identifier.identifierSystem:id1",
                                  "ids" : [ "id1.identifier.identifierSystem:id1", "id1.Patient:id1" ],
                                  "domain" : "domain",
                                  "dateShift" : 1209600.0
                                }
                                """,
                        ONLY_MATCHING_FIELDS)))
        .respond(response().withStatusCode(200));

    var consentedPatient = new ConsentedPatient("id1");
    var bundle = generateOnePatient("id1", "2024", "identifierSystem");
    var bundleFlux = Flux.just(new ConsentedPatientBundle(bundle, consentedPatient));

    create(step.deidentify(bundleFlux)).verifyComplete();
  }

  @Test
  void emptyTCAResponseYieldsEmptyResult(MockServerClient mockServer) throws IOException {
    mockServer
        .when(
            request()
                .withMethod("POST")
                .withPath("/api/v2/cd/transport-ids-and-date-shifting-values"))
        .respond(response().withStatusCode(200));

    var consentedPatient = new ConsentedPatient("id1");
    var bundle = generateOnePatient("id1", "2024", "identifierSystem");
    var bundleFlux = Flux.just(new ConsentedPatientBundle(bundle, consentedPatient));

    create(step.deidentify(bundleFlux)).verifyComplete();
  }

  @Test
  void deidentifySucceeds(MockServerClient mockServer) throws IOException {
    mockServer
        .when(
            request()
                .withMethod("POST")
                .withPath("/api/v2/cd/transport-ids-and-date-shifting-values"))
        .respond(
            response()
                .withBody(
                    json(
                        """
                                {"tIDMapName": "tIDMapName", "originalToTransportIDMap":{"id1.identifier.identifierSystem:id1":"tident1",
                                 "id1.Patient:id1":"tid1"},"dateShiftValue":1209600.000000000}
                                """))
                .withStatusCode(200));

    var consentedPatient = new ConsentedPatient("id1");
    var bundle = generateOnePatient("id1", "2024", "identifierSystem");
    var bundleFlux = Flux.just(new ConsentedPatientBundle(bundle, consentedPatient));

    create(step.deidentify(bundleFlux)).expectNextCount(1).verifyComplete();
  }

  @AfterEach
  void tearDown(MockServerClient mockServer) {
    mockServer.reset();
  }
}
