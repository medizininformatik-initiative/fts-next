package care.smith.fts.cda.impl.cohort_selector;

import static care.smith.fts.test.MockServerUtil.APPLICATION_FHIR_JSON;
import static care.smith.fts.test.MockServerUtil.clientConfig;
import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static care.smith.fts.util.error.fhir.FhirErrorResponseUtil.operationOutcomeWithIssue;
import static care.smith.fts.util.fhir.FhirUtils.toBundle;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.cda.impl.mock.MockCohortSelector;
import care.smith.fts.test.connection_scenario.AbstractConnectionScenarioIT;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
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
  private static MockCohortSelector allCohortSelector;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntime, @Autowired WebClientFactory clientFactory) {
    var address = "http://localhost";
    var server = new HttpClientConfig(address);
    var config = new FhirCohortSelectorConfig(server, PID_SYSTEM, POLICY_SYSTEM, POLICIES, "MII");
    cohortSelector =
        new FhirCohortSelector(
            config, clientFactory.create(clientConfig(wireMockRuntime)), meterRegistry);
    wireMock = wireMockRuntime.getWireMock();
  }

  private static MappingBuilder fetchAllRequest() {
    return get(urlPathEqualTo("/Patient"))
        .withQueryParam("_revinclude", equalTo("Consent:patient"))
        .withHeader(ACCEPT, equalTo(APPLICATION_FHIR_JSON));
  }

  private static MappingBuilder fetchRequest() {
    return get(urlPathEqualTo("/Patient"))
        .withQueryParam("_revinclude", equalTo("Consent:patient"))
        .withHeader(ACCEPT, equalTo(APPLICATION_FHIR_JSON));
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
            return FhirCohortSelectorIT.fetchRequest();
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
    var bundle = FhirCohortGenerator.OnePatientWithConsent(PID_SYSTEM, POLICY_SYSTEM, POLICIES);
    wireMock.register(fetchAllRequest().willReturn(fhirResponse(bundle)));
    create(cohortSelector.selectCohort(List.of())).expectNextCount(1).verifyComplete();
  }

  @Test
  void consentBundleForIdsSucceeds() {
    var bundle = FhirCohortGenerator.OnePatientWithConsent(PID_SYSTEM, POLICY_SYSTEM, POLICIES);
    wireMock.register(fetchAllRequest().willReturn(fhirResponse(bundle)));
    create(cohortSelector.selectCohort(List.of("patient-1"))).expectNextCount(1).verifyComplete();
  }

  @Test
  void emptyBundleGivesEmptyResult() {
    var emptyBundle = Stream.<Resource>of().collect(toBundle());
    wireMock.register(fetchAllRequest().willReturn(fhirResponse(emptyBundle)));
    create(cohortSelector.selectCohort(List.of())).verifyComplete();
  }

  @Test
  void paging() {
    var bundles =
        FhirCohortGenerator.PatientsWithMultipleConsentsPaged(
                PID_SYSTEM, POLICY_SYSTEM, POLICIES, 2, 1, 1)
            .toList();

    wireMock.register(fetchAllRequest().willReturn(fhirResponse(bundles.getFirst())));
    wireMock.register(
        get(urlPathEqualTo("/Consent"))
            .withQueryParam("_page", equalTo("2"))
            .willReturn(fhirResponse(bundles.get(1))));

    create(cohortSelector.selectCohort(List.of())).expectNextCount(2).verifyComplete();
  }

  @Test
  void pagingWithRetries() {
    var bundles =
        FhirCohortGenerator.PatientsWithMultipleConsentsPaged(
                PID_SYSTEM, POLICY_SYSTEM, POLICIES, 2, 1, 1)
            .toList();

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
            .willReturn(fhirResponse(bundles.getFirst())));

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
            .willReturn(fhirResponse(bundles.get(1))));

    create(cohortSelector.selectCohort(List.of())).expectNextCount(2).verifyComplete();
  }
}
