package care.smith.fts.cda.impl;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatient.ConsentedPolicies;
import care.smith.fts.api.DataSelector;
import care.smith.fts.api.Period;
import care.smith.fts.cda.test.MockServerUtil;
import care.smith.fts.util.HTTPClientConfig;
import java.io.InputStream;
import java.time.ZonedDateTime;
import org.hl7.fhir.r4.model.IdType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.model.Header;

@ExtendWith(MockServerExtension.class)
class EverythingDataSelectorTest {

  private static final String PATIENT_ID = "patient-112348";
  private static final Header CONTENT_JSON = new Header("Content-Type", "application/json");
  private static final FhirContext FHIR = FhirContext.forR4();

  private EverythingDataSelector dataSelector;

  @BeforeEach
  void setUp(MockServerClient mockServer) throws Exception {
    var address = "http://localhost:%d".formatted(mockServer.getPort());
    var server = new HTTPClientConfig(address);
    var common = new DataSelector.Config(false, null);
    this.dataSelector =
        new EverythingDataSelector(
            common,
            server.createClient(FHIR.getRestfulClientFactory()),
            pid -> new IdType("Patient", pid));
    try (var inStream = MockServerUtil.class.getResourceAsStream("metadata.json")) {
      var capStatement = requireNonNull(inStream).readAllBytes();
      mockServer
          .when(request().withMethod("GET").withPath("/metadata"))
          .respond(response().withBody(capStatement).withHeader(CONTENT_JSON));
    }
  }

  @Test
  void noConsentThrows() {
    ConsentedPolicies consentedPolicies = new ConsentedPolicies();
    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> dataSelector.select(new ConsentedPatient(PATIENT_ID, consentedPolicies)));
  }

  @Test
  void selectionSucceeds(MockServerClient mockServer) throws Exception {
    try (InputStream inStream = getClass().getResourceAsStream("patient.json")) {
      var bundle = requireNonNull(inStream).readAllBytes();
      mockServer
          .when(request().withMethod("GET").withPath("/Patient/" + PATIENT_ID + "/$everything"))
          .respond(response().withBody(bundle).withHeader(CONTENT_JSON));
    }

    ConsentedPolicies consentedPolicies = new ConsentedPolicies();
    consentedPolicies.put("pol", new Period(ZonedDateTime.now(), ZonedDateTime.now().plusYears(5)));
    assertThat(dataSelector.select(new ConsentedPatient(PATIENT_ID, consentedPolicies)))
        .isNotNull();
  }

  @AfterEach
  void tearDown(MockServerClient mockServer) {
    mockServer.reset();
  }
}
