package care.smith.fts.cda.services;

import static care.smith.fts.test.MockServerUtil.clientConfig;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static reactor.test.StepVerifier.create;

import care.smith.fts.test.MockServerUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.model.Header;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest
@ExtendWith(MockServerExtension.class)
class FhirResolveServiceTest {

  private static final String PATIENT_ID = "patient-141392";
  private static final Header CONTENT_JSON = new Header("Content-Type", "application/json");
  private static final String KDS_PATIENT = "https://some.example.com/pid";

  @Autowired WebClient.Builder builder;

  private FhirResolveService service;

  @BeforeEach
  void setUp(MockServerClient mockServer) throws Exception {
    var config = clientConfig(mockServer);
    this.service = new FhirResolveService(KDS_PATIENT, config.createClient(builder));
    try (var inStream = MockServerUtil.getResourceAsStream("metadata.json")) {
      var capStatement = requireNonNull(inStream).readAllBytes();
      mockServer
          .when(request().withMethod("GET").withPath("/metadata"))
          .respond(response().withBody(capStatement).withHeader(CONTENT_JSON));
    }
  }

  @Test
  void noPatientsErrors(MockServerClient mockServer) throws Exception {
    try (var inStream = getClass().getResourceAsStream("search-0.json")) {
      var bundle = requireNonNull(inStream).readAllBytes();
      mockServer
          .when(request().withMethod("GET").withPath("/Patient"))
          .respond(response().withBody(bundle).withHeader(CONTENT_JSON));
    }

    create(service.resolve("external-141392"))
        .verifyErrorMatches(
            err ->
                err.getMessage().contains("Unable to resolve")
                    && err.getMessage().contains("external-141392"));
  }

  @Test
  void resolveFindsPatientId(MockServerClient mockServer) throws Exception {
    try (var inStream = getClass().getResourceAsStream("search-1.json")) {
      var bundle = requireNonNull(inStream).readAllBytes();
      mockServer
          .when(request().withMethod("GET").withPath("/Patient"))
          .respond(response().withBody(bundle).withHeader(CONTENT_JSON));
    }

    create(service.resolve("external-141392"))
        .assertNext(pid -> assertThat(pid.getIdPart()).isEqualTo(PATIENT_ID))
        .verifyComplete();
  }

  @Test
  void multiplePatientsError(MockServerClient mockServer) throws Exception {
    try (var inStream = getClass().getResourceAsStream("search-2.json")) {
      var bundle = requireNonNull(inStream).readAllBytes();
      mockServer
          .when(request().withMethod("GET").withPath("/Patient"))
          .respond(response().withBody(bundle).withHeader(CONTENT_JSON));
    }

    create(service.resolve("external-075521"))
        .verifyErrorMatches(
            err ->
                err.getMessage().contains("more then one")
                    && err.getMessage().contains("external-075521"));
  }

  @Test
  void nullPatientIdThrows() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> service.resolve(null))
        .withMessageContaining("null");
  }

  @Test
  void emptyPatientIdThrows() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> service.resolve(""))
        .withMessageContaining("empty");
  }

  @AfterEach
  void tearDown(MockServerClient mockServer) {
    mockServer.reset();
  }
}
