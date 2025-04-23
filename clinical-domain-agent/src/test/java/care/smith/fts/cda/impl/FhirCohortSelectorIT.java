package care.smith.fts.cda.impl;

import static care.smith.fts.test.MockServerUtil.clientConfig;
import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static care.smith.fts.util.error.fhir.FhirErrorResponseUtil.operationOutcomeWithIssue;
import static care.smith.fts.util.fhir.FhirUtils.toBundle;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.test.connection_scenario.AbstractConnectionScenarioIT;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.error.TransferProcessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

@Slf4j
@SpringBootTest
@WireMockTest
class FhirCohortSelectorIT extends AbstractConnectionScenarioIT {

  @Autowired MeterRegistry meterRegistry;
  @Autowired ObjectMapper om;
  private WireMock wireMock;

  private static final Set<String> POLICIES = Set.of("MDAT_erheben");

  private static final String PID_SYSTEM =
      "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym";
  private static final String POLICY_SYSTEM =
      "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy";

  private static FhirCohortSelector cohortSelector;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntime, @Autowired WebClientFactory clientFactory) {
    var address = "http://localhost";
    var server = new HttpClientConfig(address);
    var config = new FhirCohortSelectorConfig(server, PID_SYSTEM, POLICY_SYSTEM, POLICIES, "MII");
    cohortSelector =
        new FhirCohortSelector(
            config, clientFactory.create(clientConfig(wireMockRuntime)), meterRegistry);
    wireMock = wireMockRuntime.getWireMock();

    // Set up basic stubs for the FHIR endpoints
    setupFhirStubs();
  }

  private void setupFhirStubs() {
    // Create stub for all consents request
    wireMock.register(
        get(urlPathEqualTo("/Consent"))
            .withQueryParam("scope", containing(POLICY_SYSTEM))
            .withQueryParam("status", equalTo("active"))
            .willReturn(aResponse().withStatus(200).withBody("{}")));

    // Create stub for patient-specific consent request
    wireMock.register(
        get(urlPathEqualTo("/Consent"))
            .withQueryParam("scope", containing(POLICY_SYSTEM))
            .withQueryParam("status", equalTo("active"))
            .withQueryParam("patient.identifier", containing(PID_SYSTEM))
            .willReturn(aResponse().withStatus(200).withBody("{}")));
  }

  private static MappingBuilder fetchAllRequest() {
    return get(urlPathEqualTo("/Consent"))
        .withQueryParam("scope", containing(POLICY_SYSTEM))
        .withQueryParam("status", equalTo("active"));
  }

  private static MappingBuilder fetchSpecificRequest(String pid) {
    return get(urlPathEqualTo("/Consent"))
        .withQueryParam("scope", containing(POLICY_SYSTEM))
        .withQueryParam("status", equalTo("active"))
        .withQueryParam("patient.identifier", containing(pid));
  }

  @Override
  protected Stream<TestStep<?>> createTestSteps() {
    return Stream.of(
        new TestStep<ConsentedPatient>() {
          @Override
          public MappingBuilder requestBuilder() {
            return FhirCohortSelectorIT.fetchAllRequest();
          }

          @Override
          public Flux<ConsentedPatient> executeStep() {
            return FhirCohortSelectorIT.cohortSelector.selectCohort(List.of());
          }
        },
        new TestStep<ConsentedPatient>() {
          @Override
          public MappingBuilder requestBuilder() {
            return FhirCohortSelectorIT.fetchSpecificRequest("id");
          }

          @Override
          public Flux<ConsentedPatient> executeStep() {
            return FhirCohortSelectorIT.cohortSelector.selectCohort(List.of("id"));
          }
        });
  }

  @Test
  void responseInvalidErrors() {
    wireMock.register(fetchAllRequest().willReturn(ok().withBody("invalid")));
    create(cohortSelector.selectCohort(List.of())).expectError().verify();
  }

  @Test
  void badRequestErrors() {
    var response =
        fhirResponse(operationOutcomeWithIssue(new Exception("FHIR Server Returns Bad Request")));
    wireMock.register(fetchAllRequest().willReturn(response.withStatus(400)));

    create(cohortSelector.selectCohort(List.of()))
        .expectErrorMessage("FHIR Server Returns Bad Request")
        .verify();
  }

  @Test
  void consentBundleSucceeds() {
    // Create a bundle with one patient consent
    Bundle bundle = new Bundle();
    bundle.setType(Bundle.BundleType.SEARCHSET);

    // Add a Consent resource to the bundle
    Consent consent = new Consent();
    consent.setId("consent-1");
    consent.setStatus(Consent.ConsentState.ACTIVE);

    // Set policy
    CodeableConcept scope = new CodeableConcept();
    Coding policy = scope.addCoding();
    policy.setSystem(POLICY_SYSTEM);
    policy.setCode(POLICIES.iterator().next());
    consent.setScope(scope);

    // Set patient
    Reference patientRef = new Reference();
    Identifier patientId = new Identifier();
    patientId.setSystem(PID_SYSTEM);
    patientId.setValue("patient123");
    patientRef.setIdentifier(patientId);
    consent.setPatient(patientRef);

    // Add entry to bundle
    bundle.addEntry().setResource(consent);

    // Register the response
    wireMock.register(fetchAllRequest().willReturn(fhirResponse(bundle)));

    create(cohortSelector.selectCohort(List.of())).expectNextCount(1).verifyComplete();
  }

  @Test
  void consentBundleForIdsSucceeds() {
    // Create a bundle with one patient consent
    Bundle bundle = new Bundle();
    bundle.setType(Bundle.BundleType.SEARCHSET);

    // Add a Consent resource to the bundle
    Consent consent = new Consent();
    consent.setId("consent-1");
    consent.setStatus(Consent.ConsentState.ACTIVE);

    // Set policy
    CodeableConcept scope = new CodeableConcept();
    Coding policy = scope.addCoding();
    policy.setSystem(POLICY_SYSTEM);
    policy.setCode(POLICIES.iterator().next());
    consent.setScope(scope);

    // Set patient
    Reference patientRef = new Reference();
    Identifier patientId = new Identifier();
    patientId.setSystem(PID_SYSTEM);
    patientId.setValue("specific-patient");
    patientRef.setIdentifier(patientId);
    consent.setPatient(patientRef);

    // Add entry to bundle
    bundle.addEntry().setResource(consent);

    // Register the response for a specific patient ID
    wireMock.register(fetchSpecificRequest("specific-patient").willReturn(fhirResponse(bundle)));

    create(cohortSelector.selectCohort(List.of("specific-patient")))
        .expectNextCount(1)
        .verifyComplete();
  }

  @Test
  void emptyBundleGivesEmptyResult() {
    var emptyBundle = Stream.<Resource>of().collect(toBundle());
    wireMock.register(fetchAllRequest().willReturn(fhirResponse(emptyBundle)));
    create(cohortSelector.selectCohort(List.of())).verifyComplete();
  }

  @Test
  void serverErrorGivesTransferProcessException() {
    wireMock.register(fetchAllRequest().willReturn(serverError()));
    create(cohortSelector.selectCohort(List.of()))
        .expectError(TransferProcessException.class)
        .verify();
  }

  @Test
  void paging() {
    // Create first page with next link
    Bundle firstPage = new Bundle();
    firstPage.setType(Bundle.BundleType.SEARCHSET);

    // Add a Consent resource to the first page
    Consent consent1 = new Consent();
    consent1.setId("consent-1");
    consent1.setStatus(Consent.ConsentState.ACTIVE);

    // Set policy
    CodeableConcept scope1 = new CodeableConcept();
    Coding policy1 = scope1.addCoding();
    policy1.setSystem(POLICY_SYSTEM);
    policy1.setCode(POLICIES.iterator().next());
    consent1.setScope(scope1);

    // Set patient
    Reference patientRef1 = new Reference();
    Identifier patientId1 = new Identifier();
    patientId1.setSystem(PID_SYSTEM);
    patientId1.setValue("patient1");
    patientRef1.setIdentifier(patientId1);
    consent1.setPatient(patientRef1);

    // Add entry to first page
    firstPage.addEntry().setResource(consent1);

    // Add next link
    firstPage.addLink().setRelation("next").setUrl("/Consent?_page=2");

    // Create second page
    Bundle secondPage = new Bundle();
    secondPage.setType(Bundle.BundleType.SEARCHSET);

    // Add a Consent resource to the second page
    Consent consent2 = new Consent();
    consent2.setId("consent-2");
    consent2.setStatus(Consent.ConsentState.ACTIVE);

    // Set policy
    CodeableConcept scope2 = new CodeableConcept();
    Coding policy2 = scope2.addCoding();
    policy2.setSystem(POLICY_SYSTEM);
    policy2.setCode(POLICIES.iterator().next());
    consent2.setScope(scope2);

    // Set patient
    Reference patientRef2 = new Reference();
    Identifier patientId2 = new Identifier();
    patientId2.setSystem(PID_SYSTEM);
    patientId2.setValue("patient2");
    patientRef2.setIdentifier(patientId2);
    consent2.setPatient(patientRef2);

    // Add entry to second page
    secondPage.addEntry().setResource(consent2);

    // Register responses
    wireMock.register(fetchAllRequest().willReturn(fhirResponse(firstPage)));
    wireMock.register(
        get(urlPathEqualTo("/Consent"))
            .withQueryParam("_page", equalTo("2"))
            .willReturn(fhirResponse(secondPage)));

    create(cohortSelector.selectCohort(List.of())).expectNextCount(2).verifyComplete();
  }

  @Test
  void pagingWithRetries() {
    // Create first page with next link
    Bundle firstPage = new Bundle();
    firstPage.setType(Bundle.BundleType.SEARCHSET);

    // Add a Consent resource to the first page
    Consent consent1 = new Consent();
    consent1.setId("consent-1");
    consent1.setStatus(Consent.ConsentState.ACTIVE);

    // Set policy
    CodeableConcept scope1 = new CodeableConcept();
    Coding policy1 = scope1.addCoding();
    policy1.setSystem(POLICY_SYSTEM);
    policy1.setCode(POLICIES.iterator().next());
    consent1.setScope(scope1);

    // Set patient
    Reference patientRef1 = new Reference();
    Identifier patientId1 = new Identifier();
    patientId1.setSystem(PID_SYSTEM);
    patientId1.setValue("patient1");
    patientRef1.setIdentifier(patientId1);
    consent1.setPatient(patientRef1);

    // Add entry to first page
    firstPage.addEntry().setResource(consent1);

    // Add next link
    firstPage.addLink().setRelation("next").setUrl("/Consent?_page=2");

    // Create second page
    Bundle secondPage = new Bundle();
    secondPage.setType(Bundle.BundleType.SEARCHSET);

    // Add a Consent resource to the second page
    Consent consent2 = new Consent();
    consent2.setId("consent-2");
    consent2.setStatus(Consent.ConsentState.ACTIVE);

    // Set policy
    CodeableConcept scope2 = new CodeableConcept();
    Coding policy2 = scope2.addCoding();
    policy2.setSystem(POLICY_SYSTEM);
    policy2.setCode(POLICIES.iterator().next());
    consent2.setScope(scope2);

    // Set patient
    Reference patientRef2 = new Reference();
    Identifier patientId2 = new Identifier();
    patientId2.setSystem(PID_SYSTEM);
    patientId2.setValue("patient2");
    patientRef2.setIdentifier(patientId2);
    consent2.setPatient(patientRef2);

    // Add entry to second page
    secondPage.addEntry().setResource(consent2);

    // Register responses with first-time failures
    wireMock.register(
        fetchAllRequest()
            .inScenario("retry-scenario")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(serverError())
            .willSetStateTo("first-retry"));

    wireMock.register(
        fetchAllRequest()
            .inScenario("retry-scenario")
            .whenScenarioStateIs("first-retry")
            .willReturn(fhirResponse(firstPage)));

    wireMock.register(
        get(urlPathEqualTo("/Consent"))
            .withQueryParam("_page", equalTo("2"))
            .inScenario("retry-scenario")
            .whenScenarioStateIs("first-retry")
            .willReturn(serverError())
            .willSetStateTo("second-retry"));

    wireMock.register(
        get(urlPathEqualTo("/Consent"))
            .withQueryParam("_page", equalTo("2"))
            .inScenario("retry-scenario")
            .whenScenarioStateIs("second-retry")
            .willReturn(fhirResponse(secondPage)));

    create(cohortSelector.selectCohort(List.of())).expectNextCount(2).verifyComplete();
  }
}
