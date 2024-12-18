package care.smith.fts.cda.services;

import static care.smith.fts.test.MockServerUtil.clientConfig;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.http.HttpStatus.OK;
import static reactor.test.StepVerifier.create;

import care.smith.fts.cda.ClinicalDomainAgent;
import care.smith.fts.test.MockServerUtil;
import care.smith.fts.util.WebClientFactory;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = ClinicalDomainAgent.class)
@WireMockTest
class FhirResolveServiceTest {

  private static final String PATIENT_ID = "patient-141392";
  private static final String KDS_PATIENT = "https://some.example.com/pid";

  @Autowired MeterRegistry meterRegistry;

  private FhirResolveService service;
  private WireMock wireMock;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wiremockRuntime, @Autowired WebClientFactory clientFactory)
      throws Exception {
    var client = clientFactory.create(clientConfig(wiremockRuntime));
    this.service = new FhirResolveService(KDS_PATIENT, client, meterRegistry);
    wireMock = wiremockRuntime.getWireMock();
    try (var inStream = MockServerUtil.getResourceAsStream("metadata.json")) {
      var capStatement = requireNonNull(inStream).readAllBytes();
      wireMock.register(get("/metadata").willReturn(jsonResponse(capStatement, OK.value())));
    }
  }

  @Test
  void noPatientsErrors() throws Exception {
    try (var inStream = getClass().getResourceAsStream("search-0.json")) {
      byte[] bundle = requireNonNull(inStream).readAllBytes();
      var response = jsonResponse(new String(bundle, UTF_8), 200);
      wireMock.register(get(urlPathEqualTo("/Patient")).willReturn(response));
    }

    create(service.resolve("external-141392"))
        .verifyErrorMatches(
            err ->
                err.getMessage().contains("Unable to resolve")
                    && err.getMessage().contains("external-141392"));
  }

  @Test
  void resolveFindsPatientId() throws Exception {
    try (var inStream = getClass().getResourceAsStream("search-1.json")) {
      var bundle = requireNonNull(inStream).readAllBytes();
      var response = jsonResponse(new String(bundle, UTF_8), 200);
      wireMock.register(get(urlPathEqualTo("/Patient")).willReturn(response));
    }

    create(service.resolve("external-141392"))
        .assertNext(pid -> assertThat(pid.getIdPart()).isEqualTo(PATIENT_ID))
        .verifyComplete();
  }

  @Test
  void multiplePatientsError() throws Exception {
    try (var inStream = getClass().getResourceAsStream("search-2.json")) {
      var bundle = requireNonNull(inStream).readAllBytes();
      var response = jsonResponse(new String(bundle, UTF_8), 200);
      wireMock.register(get(urlPathEqualTo("/Patient")).willReturn(response));
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
  void tearDown() {
    wireMock.resetMappings();
  }
}
