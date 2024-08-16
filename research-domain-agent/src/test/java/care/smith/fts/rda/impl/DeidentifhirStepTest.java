package care.smith.fts.rda.impl;

import static care.smith.fts.test.TestPatientGenerator.generateOnePatient;
import static com.typesafe.config.ConfigFactory.parseResources;
import static java.time.Duration.ofDays;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.rda.services.deidentifhir.DeidentifhirUtil;
import care.smith.fts.test.MockServerUtil;
import com.typesafe.config.Config;
import java.io.IOException;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
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
                .withBody("tIDMapName"))
        .respond(response().withStatusCode(200));

    var bundle = generateOnePatient("tid1", "2024", "identifierSystem");

    create(step.replaceIds(new TransportBundle(bundle, "tIDMapName"))).verifyComplete();
  }

  @Test
  void emptyTCAResponseYieldsEmptyResult(MockServerClient mockServer) throws IOException {
    mockServer
        .when(request().withMethod("POST").withPath("/api/v2/rd/resolve-pseudonyms"))
        .respond(response().withStatusCode(200));

    var bundle = generateOnePatient("tid1", "2024", "identifierSystem");

    create(step.replaceIds(new TransportBundle(bundle, "tIDMapName"))).verifyComplete();
  }

  @Test
  void replaceIdsSucceeds(MockServerClient mockServer) throws IOException {
    mockServer
        .when(
            request()
                .withMethod("POST")
                .withPath("/api/v2/rd/resolve-pseudonyms")
                .withBody("tIDMapName"))
        .respond(
            response()
                .withBody(
                    json(
                        """
                        {"tid1": "pid1"}
                        """))
                .withStatusCode(200));

    var bundle = generateOnePatient("tid1", "2024", "identifierSystem");

    create(step.replaceIds(new TransportBundle(bundle, "tIDMapName")))
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
