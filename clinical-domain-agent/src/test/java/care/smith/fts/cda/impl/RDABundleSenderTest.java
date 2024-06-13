package care.smith.fts.cda.impl;

import static care.smith.fts.util.auth.HTTPClientAuthMethod.AuthMethod.NONE;
import static org.mockserver.matchers.MatchType.ONLY_MATCHING_FIELDS;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.APPLICATION_JSON;
import static reactor.core.publisher.Flux.fromIterable;
import static reactor.test.StepVerifier.create;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.api.BundleSender;
import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatient.ConsentedPolicies;
import care.smith.fts.util.HTTPClientConfig;
import java.util.List;
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
  private static final ConsentedPatient PATIENT =
      new ConsentedPatient(PATIENT_ID, new ConsentedPolicies());
  @Autowired FhirContext fhir;
  @Autowired WebClient.Builder builder;

  private BundleSender<Bundle> bundleSender;

  @BeforeEach
  void setUp(MockServerClient mockServer) {
    var address = "http://localhost:%d".formatted(mockServer.getPort());
    var server = new HTTPClientConfig(address, NONE);
    var config = new RDABundleSenderConfig(server, "example");
    this.bundleSender = new RDABundleSender(config, builder.build());
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

    Bundle b = new Bundle();
    b.setEntry(
        List.of(new Bundle.BundleEntryComponent().setResource(new Patient().setId(PATIENT_ID))));
    create(bundleSender.send(fromIterable(List.of(b)), PATIENT)).expectNext().verifyComplete();
  }

  @Test
  void nullBundleThrows(MockServerClient mockServer) {
    mockServer
        .when(request().withMethod("POST").withPath("/api/v2/process/example"))
        .respond(response().withBody("{}", APPLICATION_JSON));

    create(bundleSender.send(null, PATIENT)).expectError(NullPointerException.class).verify();
  }

  @Test
  void requestThrows(MockServerClient mockServer) {
    mockServer
        .when(request().withMethod("POST").withPath("/api/v2/process/example"))
        .respond(response().withStatusCode(400));

    create(bundleSender.send(fromIterable(List.of(new Bundle())), PATIENT)).expectError().verify();
  }

  @AfterEach
  void tearDown(MockServerClient mockServer) {
    mockServer.reset();
  }
}
