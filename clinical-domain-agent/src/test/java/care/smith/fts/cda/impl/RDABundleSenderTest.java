package care.smith.fts.cda.impl;

import static care.smith.fts.cda.test.MockServerUtil.clientConfig;
import static care.smith.fts.util.FhirUtils.toBundle;
import static java.util.List.of;
import static java.util.stream.Stream.generate;
import static org.mockserver.matchers.MatchType.ONLY_MATCHING_FIELDS;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.APPLICATION_JSON;
import static reactor.core.publisher.Flux.fromIterable;
import static reactor.core.publisher.Flux.fromStream;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.BundleSender;
import care.smith.fts.api.ConsentedPatient;
import java.util.stream.Stream;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
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
class RDABundleSenderTest {

  private static final String PATIENT_ID = "patient-102931";
  private static final ConsentedPatient PATIENT = new ConsentedPatient(PATIENT_ID);

  @Autowired WebClient.Builder builder;

  private BundleSender<Bundle> bundleSender;

  @BeforeEach
  void setUp(MockServerClient mockServer) {
    var server = clientConfig(mockServer);
    var config = new RDABundleSenderConfig(server, "example");
    this.bundleSender = new RDABundleSender(config, config.server().createClient(builder));
  }

  @Test
  void bundleSent(MockServerClient mockServer) {
    mockServer
        .when(
            request()
                .withMethod("POST")
                .withPath("/api/v2/process/example")
                .withBody(
                    json(
                        """
                        {"entry": [{"resource": {"resourceType": "Patient", "id": "patient-102931"}}]}
                        """,
                        ONLY_MATCHING_FIELDS)))
        .respond(response().withStatusCode(201));

    Bundle bundle = Stream.of(new Patient().setId(PATIENT_ID)).collect(toBundle());
    create(bundleSender.send(fromIterable(of(bundle)), PATIENT))
        .expectNext(new BundleSender.Result(1))
        .verifyComplete();
  }

  @Test
  void nullBundleThrows(MockServerClient mockServer) {
    mockServer
        .when(request().withMethod("POST").withPath("/api/v2/process/example"))
        .respond(response().withBody("{}", APPLICATION_JSON));

    create(bundleSender.send(fromStream(generate(() -> null)), PATIENT))
        .expectError(NullPointerException.class)
        .verify();
  }

  @Test
  void requestThrows(MockServerClient mockServer) {
    mockServer
        .when(request().withMethod("POST").withPath("/api/v2/process/example"))
        .respond(response().withStatusCode(400));

    create(bundleSender.send(fromIterable(of(new Bundle())), PATIENT)).expectError().verify();
  }

  @AfterEach
  void tearDown(MockServerClient mockServer) {
    mockServer.reset();
  }
}
