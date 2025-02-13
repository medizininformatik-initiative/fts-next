package care.smith.fts.cda.impl;

import static care.smith.fts.test.MockServerUtil.APPLICATION_FHIR_JSON;
import static care.smith.fts.test.MockServerUtil.clientConfig;
import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static care.smith.fts.util.FhirUtils.fhirResourceToString;
import static care.smith.fts.util.FhirUtils.toBundle;
import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.cda.impl.mock.MockCohortSelector;
import care.smith.fts.test.connection_scenario.AbstractConnectionScenarioIT;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.error.TransferProcessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ProblemDetail;
import reactor.core.publisher.Flux;

@Slf4j
@SpringBootTest
@WireMockTest
class TCACohortSelectorIT extends AbstractConnectionScenarioIT {

  @Autowired MeterRegistry meterRegistry;
  @Autowired ObjectMapper om;
  private WireMock wireMock;

  private static final Set<String> POLICIES = Set.of("MDAT_erheben");

  private static final String PID_SYSTEM =
      "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym";
  private static final String POLICY_SYSTEM =
      "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy";

  private static TCACohortSelector cohortSelector;

  private static MockCohortSelector allCohortSelector;
  private static MockCohortSelector listCohortSelector;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntime, @Autowired WebClientFactory clientFactory) {
    var address = "http://localhost";
    var server = new HttpClientConfig(address);
    var config = new TCACohortSelectorConfig(server, PID_SYSTEM, POLICY_SYSTEM, POLICIES, "MII");
    cohortSelector =
        new TCACohortSelector(
            config, clientFactory.create(clientConfig(wireMockRuntime)), meterRegistry);
    wireMock = wireMockRuntime.getWireMock();
    allCohortSelector = MockCohortSelector.fetchAll(wireMock, POLICY_SYSTEM);
    listCohortSelector = MockCohortSelector.fetch(wireMock, POLICY_SYSTEM);
  }

  private static MappingBuilder getBuilderFetchAll() {
    return post("/api/v2/cd/consented-patients/fetch-all")
        .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE));
  }

  private static MappingBuilder getBuilderFetch() {
    return post("/api/v2/cd/consented-patients/fetch")
        .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE));
  }

  @Override
  protected Stream<TestStep<?>> createTestSteps() {
    return Stream.of(
        new TestStep<ConsentedPatient>() {
          @Override
          public MappingBuilder getBuilder() {
            return TCACohortSelectorIT.getBuilderFetchAll();
          }

          @Override
          public Flux<ConsentedPatient> executeStep() {
            return TCACohortSelectorIT.cohortSelector.selectCohort(List.of());
          }
        },
        new TestStep<ConsentedPatient>() {
          @Override
          public MappingBuilder getBuilder() {
            return TCACohortSelectorIT.getBuilderFetch();
          }

          @Override
          public Flux<ConsentedPatient> executeStep() {
            return TCACohortSelectorIT.cohortSelector.selectCohort(List.of("id"));
          }

          @Override
          public String acceptedContentType() {
            return APPLICATION_JSON_VALUE;
          }
        });
  }

  @Test
  void responseInvalidErrors() {
    wireMock.register(getBuilderFetchAll().willReturn(ok().withBody("invalid")));
    create(cohortSelector.selectCohort(List.of())).expectError().verify();
  }

  @Test
  void badRequestErrors() throws JsonProcessingException {
    var om = new ObjectMapper();
    wireMock.register(
        getBuilderFetchAll()
            .willReturn(
                badRequest()
                    .withBody(
                        om.writeValueAsString(
                            ProblemDetail.forStatusAndDetail(BAD_REQUEST, "Some TCA Error")))));

    create(cohortSelector.selectCohort(List.of())).expectError().verify();
  }

  @Test
  void consentBundleSucceeds() {
    allCohortSelector.consentForOnePatient("patient");
    create(cohortSelector.selectCohort(List.of())).expectNextCount(1).verifyComplete();
  }

  @Test
  void consentBundleForIdsSucceeds() {
    listCohortSelector.consentForOnePatient("patient");
    create(cohortSelector.selectCohort(List.of("patient0"))).expectNextCount(1).verifyComplete();
  }

  @Test
  void emptyOuterBundleGivesEmptyResult() {
    var outer = Stream.<Resource>of().collect(toBundle());

    wireMock.register(
        getBuilderFetchAll()
            .willReturn(
                ok().withHeader(CONTENT_TYPE, APPLICATION_FHIR_JSON)
                    .withBody(fhirResourceToString(outer))));
    create(cohortSelector.selectCohort(List.of())).verifyComplete();
  }

  @Test
  void emptyInnerBundleGivesEmptyResult() {
    var inner = Stream.of(Stream.<Resource>of().collect(toBundle())).collect(toBundle());
    wireMock.register(
        getBuilderFetchAll()
            .willReturn(
                ok().withHeader(CONTENT_TYPE, APPLICATION_FHIR_JSON)
                    .withBody(fhirResourceToString(inner))));
    create(cohortSelector.selectCohort(List.of())).verifyComplete();
  }

  @Test
  void unknownDomain() throws JsonProcessingException {

    allCohortSelector.unknownDomain(om);
    create(cohortSelector.selectCohort(List.of()))
        .expectError(TransferProcessException.class)
        .verify();
  }

  @Test
  void unknownDomainWrongError() {
    String body = "\"Parse me if you can\"";
    wireMock.register(getBuilderFetchAll().willReturn(jsonResponse(body, 400)));
    create(cohortSelector.selectCohort(List.of()))
        .expectError(TransferProcessException.class)
        .verify();
  }

  @Test
  void paging() {
    var total = 7;
    int maxPageSize = 2;
    allCohortSelector.consentForNPatientsWithPaging("pid", total, maxPageSize);
    create(cohortSelector.selectCohort(List.of())).expectNextCount(7).verifyComplete();
  }

  @Test
  void pagingWithRetries() {
    var total = 7;
    int maxPageSize = 2;
    allCohortSelector.consentForNPatientsWithPaging(
        "pid", total, maxPageSize, List.of(200, 500, 500, 200, 200, 500, 200));
    create(cohortSelector.selectCohort(List.of())).expectNextCount(7).verifyComplete();
  }
}
