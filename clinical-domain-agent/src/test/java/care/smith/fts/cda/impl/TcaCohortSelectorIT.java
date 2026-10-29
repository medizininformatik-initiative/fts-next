package care.smith.fts.cda.impl;

import static care.smith.fts.cda.impl.TcaCohortSelectorFactory.consentedPatientExtractor;
import static care.smith.fts.test.MalformedConsentDates.encodeWithMalformedDates;
import static care.smith.fts.test.MalformedConsentDates.provisionWithPlaceholderStart;
import static care.smith.fts.test.MockServerUtil.clientConfig;
import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static care.smith.fts.test.MockServerUtil.jsonResponse;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;
import static care.smith.fts.util.error.fhir.FhirErrorResponseUtil.operationOutcomeWithIssue;
import static care.smith.fts.util.fhir.FhirUtils.toBundle;
import static care.smith.fts.util.fhir.FhirUtils.typedResourceStream;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.CohortEntry;
import care.smith.fts.api.CohortEntry.Excluded;
import care.smith.fts.cda.impl.mock.MockCohortSelector;
import care.smith.fts.test.connection_scenario.AbstractConnectionScenarioIT;
import care.smith.fts.util.DefaultRetryStrategy;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.error.TransferProcessException;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@SpringBootTest
@WireMockTest
class TcaCohortSelectorIT {

  @Autowired MeterRegistry meterRegistry;
  @Autowired ObjectMapper om;
  private WireMock wireMock;

  private static final Set<String> POLICIES = Set.of("MDAT_erheben");

  private static final String PID_SYSTEM = "https://fts.smith.care";
  private static final String POLICY_SYSTEM =
      "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy";
  private static final String GICS_IDENTIFIER_SYSTEM =
      "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym";

  private static TcaCohortSelector cohortSelector;

  private static MockCohortSelector allCohortSelector;
  private static MockCohortSelector listCohortSelector;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntime, @Autowired WebClientFactory clientFactory) {
    var address = "http://localhost";
    var server = new HttpClientConfig(address);
    var config =
        new TcaCohortSelectorConfig(server, PID_SYSTEM, POLICY_SYSTEM, POLICIES, "MII", null);
    cohortSelector =
        new TcaCohortSelector(
            config,
            clientFactory.create(clientConfig(wireMockRuntime)),
            consentedPatientExtractor(config),
            new DefaultRetryStrategy(meterRegistry));
    wireMock = wireMockRuntime.getWireMock();
    allCohortSelector = MockCohortSelector.fetchAll(wireMock);
    listCohortSelector = MockCohortSelector.fetch(wireMock);
  }

  private static MappingBuilder fetchAllRequest() {
    return post("/api/v2/cd/consented-patients/fetch-all")
        .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE))
        .withHeader(ACCEPT, equalTo(APPLICATION_FHIR_JSON_VALUE));
  }

  private static MappingBuilder fetchListRequest() {
    return post("/api/v2/cd/consented-patients/fetch")
        .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE))
        .withHeader(ACCEPT, equalTo(APPLICATION_FHIR_JSON_VALUE));
  }

  @Nested
  public class FetchAllRequest extends AbstractConnectionScenarioIT {
    @Override
    protected TestStep<?> createTestStep() {
      return new TestStep<CohortEntry>() {
        @Override
        public MappingBuilder requestBuilder() {
          return TcaCohortSelectorIT.fetchAllRequest();
        }

        @Override
        public Flux<CohortEntry> executeStep() {
          return TcaCohortSelectorIT.cohortSelector.selectCohort(List.of());
        }
      };
    }
  }

  @Nested
  public class FetchListRequest extends AbstractConnectionScenarioIT {
    @Override
    protected TestStep<?> createTestStep() {
      return new TestStep<CohortEntry>() {
        @Override
        public MappingBuilder requestBuilder() {
          return TcaCohortSelectorIT.fetchListRequest();
        }

        @Override
        public Flux<CohortEntry> executeStep() {
          return TcaCohortSelectorIT.cohortSelector.selectCohort(List.of("id"));
        }

        @Override
        public String acceptedContentType() {
          return APPLICATION_JSON_VALUE;
        }
      };
    }
  }

  @Test
  void responseInvalidErrors() {
    wireMock.register(fetchAllRequest().willReturn(ok().withBody("invalid")));
    create(cohortSelector.selectCohort(List.of()))
        .expectError(TransferProcessException.class)
        .verify();
  }

  @Test
  void badRequestErrors() {
    var response =
        fhirResponse(operationOutcomeWithIssue(new Exception("TCA Returns Bad Request")));
    wireMock.register(fetchAllRequest().willReturn(response.withStatus(400)));

    create(cohortSelector.selectCohort(List.of()))
        .expectErrorMessage("TCA Returns Bad Request")
        .verify();
  }

  @Test
  void consentBundleSucceeds() {
    var consent = allCohortSelector.generateConsents("patient", 1).getFirst();
    wireMock.register(
        fetchAllRequest()
            .withRequestBody(
                equalToJson(
                    """
                    {"policies": ["MDAT_erheben"],
                     "policySystem": "%s",
                     "domain": "MII"}
                    """
                        .formatted(POLICY_SYSTEM),
                    true,
                    false))
            .willReturn(fhirResponse(Stream.of(consent).collect(toBundle()))));

    create(cohortSelector.selectCohort(List.of())).expectNextCount(1).verifyComplete();
  }

  @Test
  void consentBundleForIdsSucceeds() {
    var consent = listCohortSelector.generateConsents("patient", 1).getFirst();
    wireMock.register(
        fetchListRequest()
            .withRequestBody(
                equalToJson(
                    """
                    {"policies": ["MDAT_erheben"],
                     "policySystem": "%s",
                     "domain": "MII",
                     "patientIdentifierSystem": "%s",
                     "identifiers": ["patient0"]}
                    """
                        .formatted(POLICY_SYSTEM, GICS_IDENTIFIER_SYSTEM),
                    true,
                    false))
            .willReturn(fhirResponse(Stream.of(consent).collect(toBundle()))));

    create(cohortSelector.selectCohort(List.of("patient0"))).expectNextCount(1).verifyComplete();
  }

  /**
   * The TCA forwards a patient whose only provision for the required policy has a malformed date,
   * so the patient is excluded with a reason.
   */
  @Test
  void consentWithMalformedDateOfRequiredPolicyExcludesPatient() {
    var inner = allCohortSelector.generateConsents("patient", 1).getFirst();
    typedResourceStream(inner, Consent.class)
        .forEach(
            c ->
                c.getProvision()
                    .setProvision(
                        List.of(provisionWithPlaceholderStart(POLICY_SYSTEM, "MDAT_erheben"))));
    var outer = Stream.of(inner).collect(toBundle());
    wireMock.register(fetchAllRequest().willReturn(fhirResponse(encodeWithMalformedDates(outer))));

    create(cohortSelector.selectCohort(List.of()))
        .assertNext(
            e ->
                assertThat(e)
                    .isInstanceOf(Excluded.class)
                    .extracting(entry -> ((Excluded) entry).reason())
                    .isEqualTo("Consent for policy MDAT_erheben has an unreadable date"))
        .verifyComplete();
  }

  /**
   * The TCA forwards a patient whose consent holds all policies, but also a provision with a
   * malformed date. Only that provision grants no consent, so the patient is still selected.
   */
  @Test
  void consentWithMalformedDateIsRead() {
    var inner = allCohortSelector.generateConsents("patient", 1).getFirst();
    typedResourceStream(inner, Consent.class)
        .forEach(
            c ->
                c.getProvision()
                    .addProvision(provisionWithPlaceholderStart(POLICY_SYSTEM, "MDAT_speichern")));
    var outer = Stream.of(inner).collect(toBundle());
    wireMock.register(fetchAllRequest().willReturn(fhirResponse(encodeWithMalformedDates(outer))));

    create(cohortSelector.selectCohort(List.of())).expectNextCount(1).verifyComplete();
  }

  @Test
  void emptyOuterBundleGivesEmptyResult() {
    var outer = Stream.<Resource>of().collect(toBundle());
    wireMock.register(fetchAllRequest().willReturn(fhirResponse(outer)));
    create(cohortSelector.selectCohort(List.of())).verifyComplete();
  }

  @Test
  void emptyInnerBundleGivesEmptyResult() {
    var inner = Stream.of(Stream.<Resource>of().collect(toBundle())).collect(toBundle());
    wireMock.register(fetchAllRequest().willReturn(fhirResponse(inner)));
    create(cohortSelector.selectCohort(List.of())).verifyComplete();
  }

  @Test
  void unknownDomain() {
    allCohortSelector.unknownDomain();
    create(cohortSelector.selectCohort(List.of()))
        .expectError(TransferProcessException.class)
        .verify();
  }

  @Test
  void unknownDomainWrongError() {
    var body = "\"Parse me if you can\"";
    wireMock.register(fetchAllRequest().willReturn(jsonResponse(body, BAD_REQUEST)));
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

  @Test
  void nullSignerIdTypeDefaultsToPseudonym(
      @Autowired WebClientFactory clientFactory, WireMockRuntimeInfo wireMockRuntime) {
    // Test that null signerIdType defaults to "Pseudonym" for backward compatibility
    var server = new HttpClientConfig("http://localhost");
    var configWithNull =
        new TcaCohortSelectorConfig(server, PID_SYSTEM, POLICY_SYSTEM, POLICIES, "MII", null);
    var configWithPseudonym =
        new TcaCohortSelectorConfig(
            server, PID_SYSTEM, POLICY_SYSTEM, POLICIES, "MII", "Pseudonym");

    var selectorWithNull =
        new TcaCohortSelector(
            configWithNull,
            clientFactory.create(clientConfig(wireMockRuntime)),
            consentedPatientExtractor(configWithNull),
            new DefaultRetryStrategy(meterRegistry));
    var selectorWithPseudonym =
        new TcaCohortSelector(
            configWithPseudonym,
            clientFactory.create(clientConfig(wireMockRuntime)),
            consentedPatientExtractor(configWithPseudonym),
            new DefaultRetryStrategy(meterRegistry));

    // Both should behave identically
    var selector = MockCohortSelector.fetchAll(wireMock);
    selector.consentForOnePatient("patient");

    // Both selectors should find the same patient since null defaults to "Pseudonym"
    create(selectorWithNull.selectCohort(List.of())).expectNextCount(1).verifyComplete();
    create(selectorWithPseudonym.selectCohort(List.of())).expectNextCount(1).verifyComplete();
  }
}
