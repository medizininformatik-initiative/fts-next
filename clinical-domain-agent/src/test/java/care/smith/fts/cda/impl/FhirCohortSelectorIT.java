package care.smith.fts.cda.impl;

import static care.smith.fts.test.MockServerUtil.APPLICATION_FHIR_JSON;
import static care.smith.fts.test.MockServerUtil.clientConfig;
import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static care.smith.fts.util.fhir.FhirUtils.toBundle;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.test.connection_scenario.AbstractConnectionScenarioIT;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

@Slf4j
@SpringBootTest
@WireMockTest
class FhirCohortSelectorIT {

  @Autowired MeterRegistry meterRegistry;
  private WireMock wireMock;

  private static final Set<String> POLICIES = Set.of("MDAT_erheben");

  private static final String PID_SYSTEM =
      "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym";
  private static final String POLICY_SYSTEM =
      "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy";

  private FhirCohortSelector cohortSelector;

  private FhirCohortGenerator cohortGenerator;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntime, @Autowired WebClientFactory clientFactory) {
    var ignored = new HttpClientConfig("ignored");
    var config = new FhirCohortSelectorConfig(ignored, PID_SYSTEM, POLICY_SYSTEM, POLICIES);
    var fhirClient = clientFactory.create(clientConfig(wireMockRuntime));
    cohortSelector = new FhirCohortSelector(config, fhirClient, meterRegistry);
    wireMock = wireMockRuntime.getWireMock();
    cohortGenerator = new FhirCohortGenerator(PID_SYSTEM, POLICY_SYSTEM, POLICIES);
  }

  private static MappingBuilder fetchAllRequest() {
    return get(urlPathEqualTo("/Patient"))
        .withQueryParam("_revinclude", equalTo("Consent:patient"))
        .withHeader(ACCEPT, equalTo(APPLICATION_FHIR_JSON));
  }

  private static MappingBuilder fetchListRequest() {
    return get(urlPathEqualTo("/Patient"))
        .withQueryParam("_revinclude", equalTo("Consent:patient"))
        .withQueryParam("identifier", equalTo(PID_SYSTEM + "|patient-1"))
        .withHeader(ACCEPT, equalTo(APPLICATION_FHIR_JSON));
  }

  @Nested
  public class FetchAllRequest extends AbstractConnectionScenarioIT {
    @Override
    protected TestStep<?> createTestStep() {
      return new TestStep<ConsentedPatient>() {
        @Override
        public MappingBuilder requestBuilder() {
          return fetchAllRequest();
        }

        @Override
        public Flux<ConsentedPatient> executeStep() {
          return cohortSelector.selectCohort(List.of());
        }
      };
    }
  }

  @Nested
  public class FetchListRequest extends AbstractConnectionScenarioIT {
    @Override
    protected TestStep<?> createTestStep() {
      return new TestStep<ConsentedPatient>() {
        @Override
        public MappingBuilder requestBuilder() {
          return fetchListRequest();
        }

        @Override
        public Flux<ConsentedPatient> executeStep() {
          return cohortSelector.selectCohort(List.of("patient-1"));
        }
      };
    }
  }

  @Test
  void responseInvalidErrors() {
    wireMock.register(fetchAllRequest().willReturn(ok().withBody("invalid")));
    create(cohortSelector.selectCohort(List.of())).expectError().verify();
  }

  @Test
  void consentBundleSucceeds() {
    var bundle = cohortGenerator.generate();
    wireMock.register(fetchAllRequest().willReturn(fhirResponse(bundle)));
    create(cohortSelector.selectCohort(List.of())).expectNextCount(1).verifyComplete();
  }

  @Test
  void consentBundleForIdsSucceeds() {
    var bundle = cohortGenerator.generate();
    wireMock.register(fetchListRequest().willReturn(fhirResponse(bundle)));

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
    var bundles = cohortGenerator.generate(2, 1, 1).toList();

    wireMock.register(fetchAllRequest().willReturn(fhirResponse(bundles.getFirst())));
    wireMock.register(
        get(urlPathEqualTo("/Consent"))
            .withQueryParam("_page", equalTo("1"))
            .willReturn(fhirResponse(bundles.get(1))));

    create(cohortSelector.selectCohort(List.of())).expectNextCount(2).verifyComplete();
  }

  @Test
  void pagingWithRetries() {
    var bundles = cohortGenerator.generate(2, 1, 1).toList();

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
            .withQueryParam("_page", equalTo("1"))
            .inScenario("retry-scenario")
            .whenScenarioStateIs("first-retry")
            .willReturn(serverError())
            .willSetStateTo("second-retry"));

    wireMock.register(
        get(urlPathEqualTo("/Consent"))
            .withQueryParam("_page", equalTo("1"))
            .inScenario("retry-scenario")
            .whenScenarioStateIs("second-retry")
            .willReturn(fhirResponse(bundles.get(1))));

    create(cohortSelector.selectCohort(List.of())).expectNextCount(2).verifyComplete();
  }
}
