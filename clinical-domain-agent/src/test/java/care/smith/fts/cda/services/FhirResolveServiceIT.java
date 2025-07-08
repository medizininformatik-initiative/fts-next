package care.smith.fts.cda.services;

import static care.smith.fts.test.FhirGenerators.randomUuid;
import static care.smith.fts.test.FhirGenerators.resolveSearchResponse;
import static care.smith.fts.test.MockServerUtil.APPLICATION_FHIR_JSON;
import static care.smith.fts.test.MockServerUtil.clientConfig;
import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static care.smith.fts.util.fhir.FhirUtils.toBundle;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.google.common.net.HttpHeaders.ACCEPT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.*;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.cda.ClinicalDomainAgent;
import care.smith.fts.test.MockServerUtil;
import care.smith.fts.test.connection_scenario.AbstractConnectionScenarioIT;
import care.smith.fts.util.WebClientFactory;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IIdType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@SpringBootTest(classes = ClinicalDomainAgent.class)
@WireMockTest
class FhirResolveServiceIT extends AbstractConnectionScenarioIT {

  private static final String PATIENT_ID = "patient-141392";
  public static final String PID_SYSTEM = "patientIdentifierSystem";

  @Autowired MeterRegistry meterRegistry;

  private FhirResolveService service;
  private WireMock wireMock;
  private WebClient client;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wiremockRuntime, @Autowired WebClientFactory clientFactory)
      throws Exception {
    client = clientFactory.create(clientConfig(wiremockRuntime));
    this.service = new FhirResolveService(PID_SYSTEM, client, meterRegistry);
    wireMock = wiremockRuntime.getWireMock();
    try (var inStream = MockServerUtil.getResourceAsStream("metadata.json")) {
      var capStatement = new String(requireNonNull(inStream).readAllBytes(), UTF_8);
      wireMock.register(get("/metadata").willReturn(fhirResponse(capStatement)));
    }
  }

  private static MappingBuilder fhirStoreRequest() {
    return get(urlPathEqualTo("/Patient")).withHeader(ACCEPT, equalTo(APPLICATION_FHIR_JSON));
  }

  @Override
  protected TestStep<?> createTestStep() {
    return new TestStep<IIdType>() {
      @Override
      public MappingBuilder requestBuilder() {
        return FhirResolveServiceIT.fhirStoreRequest();
      }

      @Override
      public Mono<IIdType> executeStep() {
        return service.resolve(new ConsentedPatient(PATIENT_ID, PID_SYSTEM));
      }

      @Override
      public String acceptedContentType() {
        return APPLICATION_FHIR_JSON;
      }
    };
  }

  @Test
  void noPatientsErrors() throws Exception {
    try (var inStream = getClass().getResourceAsStream("search-0.json")) {
      byte[] bundle = requireNonNull(inStream).readAllBytes();
      var response = fhirResponse(new String(bundle, UTF_8));
      wireMock.register(fhirStoreRequest().willReturn(response));
    }

    create(service.resolve(new ConsentedPatient("external-141392", PID_SYSTEM)))
        .verifyErrorMatches(
            err ->
                err.getMessage().contains("Unable to resolve")
                    && err.getMessage().contains("external-141392"));
  }

  @Test
  void resolveFindsPatientId() throws Exception {
    try (var inStream = getClass().getResourceAsStream("search-1.json")) {
      var bundle = requireNonNull(inStream).readAllBytes();
      var response = fhirResponse(new String(bundle, UTF_8));
      wireMock.register(fhirStoreRequest().willReturn(response));
    }

    create(service.resolve(new ConsentedPatient("external-141392", PID_SYSTEM)))
        .assertNext(pid -> assertThat(pid.getIdPart()).isEqualTo(PATIENT_ID))
        .verifyComplete();
  }

  @Test
  void multiplePatientsError() throws Exception {
    try (var inStream = getClass().getResourceAsStream("search-2.json")) {
      var bundle = requireNonNull(inStream).readAllBytes();
      var response = fhirResponse(new String(bundle, UTF_8));
      wireMock.register(fhirStoreRequest().willReturn(response));
    }

    create(service.resolve(new ConsentedPatient("external-075521", PID_SYSTEM)))
        .verifyErrorMatches(
            err ->
                err.getMessage().contains("more then one")
                    && err.getMessage().contains("external-075521"));
  }

  @Test
  void nullPatientIdThrows() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> service.resolve(new ConsentedPatient(null, PID_SYSTEM)))
        .withMessageContaining("null");
  }

  @Test
  void emptyPatientIdThrows() {
    assertThatExceptionOfType(NullPointerException.class)
        .isThrownBy(() -> service.resolve(new ConsentedPatient("", PID_SYSTEM)))
        .withMessageContaining("empty");
  }

  @Test
  void hdsReturnsMoreThanOneResult() throws IOException {
    var fhirResolveGen = resolveSearchResponse(() -> "id1", randomUuid());
    var bundle = fhirResolveGen.generateResources().limit(2).collect(toBundle());
    wireMock.register(fhirStoreRequest().willReturn(fhirResponse(bundle)));
    create(service.resolve(new ConsentedPatient(PATIENT_ID, PID_SYSTEM)))
        .expectError(IllegalStateException.class)
        .verify();
  }

  @Test
  void pisFromResolverHasPrecedence() throws Exception {
    var resolverSystem = "https://resolver.example.com/pid";
    var serviceWithConfiguredSystem = new FhirResolveService(resolverSystem, client, meterRegistry);

    var patient = new ConsentedPatient("patient-123", "https://patient.example.com/pid");

    try (var inStream = getClass().getResourceAsStream("search-1.json")) {
      var bundle = requireNonNull(inStream).readAllBytes();
      var response = fhirResponse(new String(bundle, UTF_8));
      wireMock.register(
          get(urlPathEqualTo("/Patient"))
              .withQueryParam("identifier", equalTo(resolverSystem + "|patient-123"))
              .withHeader(ACCEPT, equalTo(APPLICATION_FHIR_JSON))
              .willReturn(response));
    }

    create(serviceWithConfiguredSystem.resolve(patient))
        .assertNext(pid -> assertThat(pid.getIdPart()).isEqualTo(PATIENT_ID))
        .verifyComplete();

    wireMock.verify(
        getRequestedFor(urlPathEqualTo("/Patient"))
            .withQueryParam("identifier", equalTo(resolverSystem + "|patient-123")));
  }

  @Test
  void usePisFromConsentedPatient() throws Exception {
    var serviceWithNullSystem = new FhirResolveService(null, client, meterRegistry);

    var patientSystem = "https://patient.example.com/pid";
    var patient = new ConsentedPatient("patient-456", patientSystem);

    try (var inStream = getClass().getResourceAsStream("search-1.json")) {
      var bundle = requireNonNull(inStream).readAllBytes();
      var response = fhirResponse(new String(bundle, UTF_8));
      wireMock.register(
          get(urlPathEqualTo("/Patient"))
              .withQueryParam("identifier", equalTo(patientSystem + "|patient-456"))
              .withHeader(ACCEPT, equalTo(APPLICATION_FHIR_JSON))
              .willReturn(response));
    }

    create(serviceWithNullSystem.resolve(patient))
        .assertNext(pid -> assertThat(pid.getIdPart()).isEqualTo(PATIENT_ID))
        .verifyComplete();

    wireMock.verify(
        getRequestedFor(urlPathEqualTo("/Patient"))
            .withQueryParam("identifier", equalTo(patientSystem + "|patient-456")));
  }

  @AfterEach
  void tearDown() {
    wireMock.resetMappings();
  }
}
