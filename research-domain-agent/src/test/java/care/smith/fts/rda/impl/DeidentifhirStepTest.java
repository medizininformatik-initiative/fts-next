package care.smith.fts.rda.impl;

import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static com.typesafe.config.ConfigFactory.parseResources;
import static java.time.Duration.ofDays;
import static org.mockserver.matchers.MatchType.ONLY_MATCHING_FIELDS;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.rda.services.deidentifhir.DeidentifhirUtil;
import care.smith.fts.test.MockServerUtil;
import com.typesafe.config.Config;
import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.springframework.web.reactive.function.client.WebClient;

@ExtendWith(MockServerExtension.class)
class DeidentifhirStepTest {
  private DeidentifhirStep step;

  @BeforeEach
  void setUp(MockServerClient mockServer) {
    Config config = parseResources(DeidentifhirUtil.class, "TransportToRD.profile");
    var server = MockServerUtil.clientConfig(mockServer);

    step =
        new DeidentifhirStep(
            config, server.createClient(WebClient.builder()), "domain", ofDays(14));
  }

  @AfterEach
  void tearDown(MockServerClient mockServer) {
    mockServer.reset();
  }

  @Test
  void correctRequestSent(MockServerClient mockServer) throws IOException {
    mockServer
        .when(
            request()
                .withMethod("POST")
                .withPath("/api/v2/rd/resolve-pseudonyms")
                .withBody(
                    json(
                        """
                               {
                                 "domain": "domain",
                                 "ids": ["tid1"]
                               }
                               """,
                        ONLY_MATCHING_FIELDS)))
        .respond(response().withStatusCode(200));

    var bundle = generateOnePatient("tid1", "2024", "identifierSystem");

    create(step.replaceIds(new TransportBundle(bundle, Set.of("tid1")))).verifyComplete();
  }

  @Test
  void emptyTCAResponseYieldsEmptyResult(MockServerClient mockServer) throws IOException {
    mockServer
        .when(request().withMethod("POST").withPath("/api/v2/rd/resolve-pseudonyms"))
        .respond(response().withStatusCode(200));

    var bundle = generateOnePatient("tid1", "2024", "identifierSystem");

    create(step.replaceIds(new TransportBundle(bundle, Set.of("tid1")))).verifyComplete();
  }

  @Test
  void replaceIdsSucceeds(MockServerClient mockServer) throws IOException {
    mockServer
        .when(
            request()
                .withMethod("POST")
                .withPath("/api/v2/rd/resolve-pseudonyms")
                .withBody(
                    json(
                        """
                                           {
                                             "domain": "domain",
                                             "ids": ["tid1"]
                                           }
                                           """,
                        ONLY_MATCHING_FIELDS)))
        .respond(
            response()
                .withBody(
                    json(
                        """
                        {"idMap":  {"tid1": "pid1"}, "dateShiftValue": 1234 }
                        """))
                .withStatusCode(200));

    var bundle = generateOnePatient("tid1", "2024", "identifierSystem");

    create(step.replaceIds(new TransportBundle(bundle, Set.of("tid1"))))
        .expectNextCount(1)
        .verifyComplete();
  }
}
