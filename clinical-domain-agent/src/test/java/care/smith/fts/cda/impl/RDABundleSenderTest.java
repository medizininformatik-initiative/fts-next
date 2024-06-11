package care.smith.fts.cda.impl;

import static care.smith.fts.util.auth.HTTPClientAuthMethod.AuthMethod.NONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockserver.matchers.MatchType.ONLY_MATCHING_FIELDS;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.APPLICATION_JSON;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.api.BundleSender;
import care.smith.fts.util.HTTPClientConfig;
import java.util.List;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
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

@SpringBootTest
@ExtendWith(MockServerExtension.class)
class RDABundleSenderTest {

  @Autowired FhirContext fhir;
  @Autowired HttpClientBuilder httpClient;

  private BundleSender<Bundle> bundleSender;

  @BeforeEach
  void setUp(MockServerClient mockServer) {
    var address = "http://localhost:%d".formatted(mockServer.getPort());
    var server = new HTTPClientConfig(address, NONE);
    var config = new RDABundleSenderConfig(server);
    this.bundleSender = new RDABundleSender(config, httpClient.build(), fhir);
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
                        {"entry": [{"resource": {"resourceType": "Patient", "id": "patient-125419"}}]}
                        """,
                        ONLY_MATCHING_FIELDS)))
        .respond(response().withStatusCode(201));

    Bundle b = new Bundle();
    b.setEntry(
        List.of(
            new Bundle.BundleEntryComponent().setResource(new Patient().setId("patient-125419"))));
    assertThat(bundleSender.send(b, "example")).isTrue();
  }

  @Test
  void nullBundleThrows(MockServerClient mockServer) {
    mockServer
        .when(request().withMethod("POST").withPath("/api/v2/process/example"))
        .respond(response().withBody("{}", APPLICATION_JSON));

    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> bundleSender.send(null, "example"))
        .withMessageContaining("null");
  }

  @Test
  void requestThrows(MockServerClient mockServer) {
    mockServer
        .when(request().withMethod("POST").withPath("/api/v2/process/example"))
        .respond(response().withStatusCode(400));

    assertThat(bundleSender.send(new Bundle(), "example")).isFalse();
  }

  @AfterEach
  void tearDown(MockServerClient mockServer) {
    mockServer.reset();
  }
}
