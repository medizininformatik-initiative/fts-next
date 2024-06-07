package care.smith.fts.cda.services;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import care.smith.fts.cda.test.MockServerUtil;
import org.hl7.fhir.instance.model.api.IIdType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.model.Header;

@ExtendWith(MockServerExtension.class)
class FhirResolveServiceTest {

  private static final String PATIENT_ID = "patient-141392";
  private static final Header CONTENT_JSON = new Header("Content-Type", "application/json");
  private static final FhirContext FHIR = FhirContext.forR4();
  private static final String KDS_PATIENT = "https://some.example.com/pid";

  private FhirResolveService service;

  @BeforeEach
  void setUp(MockServerClient mockServer) throws Exception {
    var address = "http://localhost:%d".formatted(mockServer.getPort());
    IGenericClient client = FHIR.newRestfulGenericClient(address);
    this.service = new FhirResolveService(KDS_PATIENT, client, FHIR);
    try (var inStream = MockServerUtil.class.getResourceAsStream("metadata.json")) {
      var capStatement = requireNonNull(inStream).readAllBytes();
      mockServer
          .when(request().withMethod("GET").withPath("/metadata"))
          .respond(response().withBody(capStatement).withHeader(CONTENT_JSON));
    }
  }

  @Test
  void noPatientsThrow(MockServerClient mockServer) throws Exception {
    try (var inStream = getClass().getResourceAsStream("search-0.json")) {
      var bundle = requireNonNull(inStream).readAllBytes();
      mockServer
          .when(request().withMethod("GET").withPath("/Patient"))
          .respond(response().withBody(bundle).withHeader(CONTENT_JSON));
    }

    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> service.resolve("external-141392"))
        .withMessageContaining("Unable to resolve")
        .withMessageContaining("external-141392");
  }

  @Test
  void resolveFindsPatientId(MockServerClient mockServer) throws Exception {
    try (var inStream = getClass().getResourceAsStream("search-1.json")) {
      var bundle = requireNonNull(inStream).readAllBytes();
      mockServer
          .when(request().withMethod("GET").withPath("/Patient"))
          .respond(response().withBody(bundle).withHeader(CONTENT_JSON));
    }

    IIdType pid = service.resolve("external-141392");
    assertThat(pid.getIdPart()).isEqualTo(PATIENT_ID);
  }

  @Test
  void multiplePatientsThrow(MockServerClient mockServer) throws Exception {
    try (var inStream = getClass().getResourceAsStream("search-2.json")) {
      var bundle = requireNonNull(inStream).readAllBytes();
      mockServer
          .when(request().withMethod("GET").withPath("/Patient"))
          .respond(response().withBody(bundle).withHeader(CONTENT_JSON));
    }

    assertThatExceptionOfType(IllegalStateException.class)
        .isThrownBy(() -> service.resolve("external-141392"))
        .withMessageContaining("more then one")
        .withMessageContaining("external-141392");
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
