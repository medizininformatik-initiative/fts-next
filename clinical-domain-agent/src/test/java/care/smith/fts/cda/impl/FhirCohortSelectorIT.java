package care.smith.fts.cda.impl;

import static care.smith.fts.cda.impl.FhirCohortSelectorFactory.consentedPatientExtractor;
import static care.smith.fts.test.LogRecorder.messages;
import static care.smith.fts.test.LogRecorder.recordLog;
import static care.smith.fts.test.MalformedConsentDates.encodeWithMalformedDates;
import static care.smith.fts.test.MalformedConsentDates.provisionWithPlaceholderStart;
import static care.smith.fts.test.MockServerUtil.APPLICATION_FHIR_JSON;
import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static care.smith.fts.test.MockServerUtil.onRandomPort;
import static care.smith.fts.util.fhir.FhirUtils.toBundle;
import static care.smith.fts.util.fhir.FhirUtils.typedResourceStream;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static java.time.Duration.ofMillis;
import static java.util.concurrent.CompletableFuture.delayedExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.joining;
import static java.util.stream.IntStream.range;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.CohortEntry;
import care.smith.fts.api.CohortEntry.Consented;
import care.smith.fts.api.CohortEntry.Excluded;
import care.smith.fts.api.CohortEntry.NotConsented;
import care.smith.fts.test.FhirCohortGenerator;
import care.smith.fts.test.MockServerUtil;
import care.smith.fts.test.connection_scenario.AbstractConnectionScenarioIT;
import care.smith.fts.util.ConsentedPatientExtractor;
import care.smith.fts.util.DefaultRetryStrategy;
import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.error.TransferProcessException;
import ch.qos.logback.classic.Level;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Bundle.BundleLinkComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

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
  private HttpClientConfig config;
  private FhirCohortSelectorConfig selectorConfig;
  private WebClient fhirClient;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntime, @Autowired WebClientFactory clientFactory) {
    var ignored = new HttpClientConfig("ignored");
    selectorConfig = new FhirCohortSelectorConfig(ignored, PID_SYSTEM, POLICY_SYSTEM, POLICIES);
    this.config = MockServerUtil.clientConfig(wireMockRuntime);
    fhirClient = clientFactory.create(this.config);
    cohortSelector =
        new FhirCohortSelector(
            selectorConfig,
            fhirClient,
            consentedPatientExtractor(selectorConfig),
            new DefaultRetryStrategy(meterRegistry),
            Schedulers.parallel(),
            4);
    wireMock = wireMockRuntime.getWireMock();
    cohortGenerator = new FhirCohortGenerator(PID_SYSTEM, POLICY_SYSTEM, POLICIES);
  }

  private static MappingBuilder fetchAllRequest() {
    return get(urlPathEqualTo("/Patient"))
        .withQueryParam("_has:Consent:patient:status", equalTo("active"))
        .withQueryParam("_revinclude", absent())
        .withHeader(ACCEPT, equalTo(APPLICATION_FHIR_JSON));
  }

  private static MappingBuilder fetchListRequest(String... pids) {
    var pidList = Stream.of(pids).map(pid -> PID_SYSTEM + "|" + pid).collect(joining(","));
    return get(urlPathEqualTo("/Patient"))
        .withQueryParam("identifier", equalTo(pidList))
        .withQueryParam("_revinclude", absent())
        .withHeader(ACCEPT, equalTo(APPLICATION_FHIR_JSON));
  }

  private FhirCohortSelector cohortSelectorOn(Scheduler scheduler) {
    return new FhirCohortSelector(
        selectorConfig,
        fhirClient,
        consentedPatientExtractor(selectorConfig),
        new DefaultRetryStrategy(meterRegistry),
        scheduler,
        4);
  }

  private static MappingBuilder nextPageRequest() {
    return get(urlPathEqualTo("/Patient")).withQueryParam("_page", equalTo("1"));
  }

  private static MappingBuilder consentRequest(String... pids) {
    var pidList = Stream.of(pids).map(pid -> PID_SYSTEM + "|" + pid).collect(joining(","));
    return get(urlPathEqualTo("/Consent"))
        .withQueryParam("patient.identifier", equalTo(pidList))
        .withQueryParam("_include", equalTo("Consent:patient"))
        .withQueryParam("status", absent())
        .withHeader(ACCEPT, equalTo(APPLICATION_FHIR_JSON));
  }

  /**
   * Registers the patients of {@code page} as response to {@code patientSearch}, and its consents
   * as response to the consent request for these patients.
   */
  private void registerPage(MappingBuilder patientSearch, Bundle page) {
    registerPage(patientSearch, page, MockServerUtil::fhirResponse);
  }

  private void registerPage(
      MappingBuilder patientSearch,
      Bundle page,
      Function<Bundle, ResponseDefinitionBuilder> consentResponse) {
    wireMock.register(patientSearch.willReturn(fhirResponse(patientsOf(page))));
    registerConsentsOf(page, consentResponse);
  }

  private void registerConsentsOf(Bundle page) {
    registerConsentsOf(page, MockServerUtil::fhirResponse);
  }

  private void registerConsentsOf(
      Bundle page, Function<Bundle, ResponseDefinitionBuilder> consentResponse) {
    var consents = typedResourceStream(page, Consent.class).collect(toBundle());
    wireMock.register(
        consentRequest(identifierValues(page)).willReturn(consentResponse.apply(consents)));
  }

  private static Bundle patientsOf(Bundle page) {
    return typedResourceStream(page, Patient.class).collect(toBundle()).setLink(page.getLink());
  }

  /** The first identifier value of the configured system of each patient of the page. */
  private static String[] identifierValues(Bundle page) {
    return typedResourceStream(page, Patient.class)
        .flatMap(
            p -> p.getIdentifier().stream().filter(i -> PID_SYSTEM.equals(i.getSystem())).limit(1))
        .map(Identifier::getValue)
        .distinct()
        .toArray(String[]::new);
  }

  @Nested
  public class FetchAllRequest extends AbstractConnectionScenarioIT {
    @Override
    protected TestStep<?> createTestStep() {
      return new TestStep<CohortEntry>() {
        @Override
        public MappingBuilder requestBuilder() {
          return fetchAllRequest();
        }

        @Override
        public Flux<CohortEntry> executeStep() {
          return cohortSelector.selectCohort(List.of());
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
          return fetchListRequest("patient-identifier-151337");
        }

        @Override
        public Flux<CohortEntry> executeStep() {
          return cohortSelector.selectCohort(List.of("patient-identifier-151337"));
        }
      };
    }
  }

  @Nested
  public class FetchConsentsRequest extends AbstractConnectionScenarioIT {
    @Override
    protected TestStep<?> createTestStep() {
      var page = Stream.of(patient("x")).collect(toBundle());
      wireMock.register(fetchAllRequest().willReturn(fhirResponse(page)));
      return new TestStep<CohortEntry>() {
        @Override
        public MappingBuilder requestBuilder() {
          return consentRequest("patient-identifier-x");
        }

        @Override
        public Flux<CohortEntry> executeStep() {
          return cohortSelector.selectCohort(List.of());
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
  void fhirServerErrorBecomesTransferProcessException() {
    wireMock.register(fetchAllRequest().willReturn(notFound()));

    create(cohortSelector.selectCohort(List.of()))
        .expectErrorSatisfies(e ->
            assertThat(e)
                .isInstanceOf(TransferProcessException.class)
                .hasMessageContaining("FHIR server"))
        .verify();
  }

  @Test
  void consentBundleSucceeds() {
    var bundle = cohortGenerator.generate();
    registerPage(fetchAllRequest(), bundle);
    create(cohortSelector.selectCohort(List.of())).expectNextCount(1).verifyComplete();
  }

  @Test
  void emptyIdentifierListOmitsIdentifierQueryParam() {
    registerPage(
        fetchAllRequest().withQueryParam("identifier", absent()), cohortGenerator.generate());

    create(cohortSelector.selectCohort(List.of())).expectNextCount(1).verifyComplete();
  }

  @Test
  void requestedPatientWithoutConsentCoveringAllPoliciesIsNotConsented() {
    var bundle = Stream.of(patient("b"), consentFor("b", "MDAT_speichern")).collect(toBundle());
    registerPage(fetchListRequest("patient-identifier-b"), bundle);

    create(cohortSelector.selectCohort(List.of("patient-identifier-b")))
        .expectNext(new NotConsented("patient-identifier-b"))
        .verifyComplete();
  }

  /**
   * Consents are searched regardless of their status, so a requested patient with only an inactive
   * consent is not consented, rather than without consent found.
   */
  @Test
  void requestedPatientWithOnlyInactiveConsentIsNotConsented() {
    var inactive = consentFor("b", "MDAT_erheben").setStatus(Consent.ConsentState.INACTIVE);
    var bundle = Stream.of(patient("b"), inactive).collect(toBundle());
    registerPage(fetchListRequest("patient-identifier-b"), bundle);

    create(cohortSelector.selectCohort(List.of("patient-identifier-b")))
        .expectNext(new NotConsented("patient-identifier-b"))
        .verifyComplete();
  }

  /**
   * The search returns requested patients without consent, too. They are left out, so they are
   * reported as without consent found, as when the server returns no patient.
   */
  @Test
  void requestedPatientWithoutConsentIsLeftOut() {
    var bundle = Stream.of(patient("b")).collect(toBundle());
    registerPage(fetchListRequest("patient-identifier-b"), bundle);

    create(cohortSelector.selectCohort(List.of("patient-identifier-b"))).verifyComplete();
  }

  @Test
  void patientWithSeveralIdentifiersIsReadByTheRequestedOne() {
    var x = patient("x");
    x.addIdentifier().setSystem(PID_SYSTEM).setValue("patient-identifier-x2");
    wireMock.register(
        fetchListRequest("patient-identifier-x2")
            .willReturn(fhirResponse(Stream.of(x).collect(toBundle()))));
    wireMock.register(
        consentRequest("patient-identifier-x2")
            .willReturn(
                fhirResponse(Stream.of(consentFor("x", "MDAT_erheben")).collect(toBundle()))));

    create(cohortSelector.selectCohort(List.of("patient-identifier-x2")))
        .assertNext(e ->
            assertThat(e)
                .isInstanceOf(Consented.class)
                .extracting(CohortEntry::identifier)
                .isEqualTo("patient-identifier-x2"))
        .verifyComplete();
  }

  @Test
  void patientWithoutConsentCoveringAllPoliciesIsNotConsentedWhenSelectingAllPatients() {
    var bundle = Stream.of(patient("b"), consentFor("b", "MDAT_speichern")).collect(toBundle());
    registerPage(fetchAllRequest(), bundle);

    create(cohortSelector.selectCohort(List.of()))
        .expectNext(new NotConsented("patient-identifier-b"))
        .verifyComplete();
  }

  @Test
  void patientWithoutIdentifierOfConfiguredSystemIsCountedInWarning() {
    var unidentified = patient("b");
    unidentified.getIdentifierFirstRep().setSystem("http://other-system");
    var bundle =
        Stream.of(
                patient("a"),
                consentFor("a", "MDAT_erheben"),
                unidentified,
                consentFor("b", "MDAT_erheben"))
            .collect(toBundle());
    registerPage(fetchAllRequest(), bundle);

    var events =
        recordLog(
            ConsentedPatientExtractor.class,
            Level.WARN,
            () ->
                create(cohortSelector.selectCohort(List.of())).expectNextCount(1).verifyComplete());

    assertThat(messages(events))
        .singleElement()
        .satisfies(m -> assertThat(m).contains("Ignoring 1 patients").doesNotContain("-b"));
  }

  @Test
  void consentsAreGroupedWithTheirOwnPatient() {
    var bundle =
        Stream.of(
                patient("a"),
                consentFor("a", "MDAT_erheben"),
                patient("b"),
                consentFor("b", "MDAT_speichern"))
            .collect(toBundle());
    registerPage(fetchAllRequest(), bundle);

    var events =
        recordLog(
            FhirCohortSelector.class,
            Level.WARN,
            () ->
                create(cohortSelector.selectCohort(List.of()).collectList())
                    .assertNext(entries ->
                        assertThat(entries)
                            .filteredOn(Consented.class::isInstance)
                            .extracting(CohortEntry::identifier)
                            .containsExactly("patient-identifier-a"))
                    .verifyComplete());

    assertThat(events).isEmpty();
  }

  /**
   * Patient b has only a consent referencing it by identifier. That consent is not assigned to any
   * patient, so b is not selected, while selection of a is not aborted.
   */
  @Test
  void consentWithLogicalPatientReferenceIsIgnored() {
    var logicalReference =
        new Reference()
            .setIdentifier(new Identifier().setSystem(PID_SYSTEM).setValue("patient-identifier-b"));
    var bundle =
        Stream.of(
                patient("a"),
                patient("b"),
                consentFor("a", "MDAT_erheben"),
                consentFor("b", "MDAT_erheben").setPatient(logicalReference))
            .collect(toBundle());
    registerPage(fetchAllRequest(), bundle);

    var events =
        recordLog(
            FhirCohortSelector.class,
            Level.WARN,
            () ->
                create(cohortSelector.selectCohort(List.of()).collectList())
                    .assertNext(entries ->
                        assertThat(entries)
                            .filteredOn(Consented.class::isInstance)
                            .extracting(CohortEntry::identifier)
                            .containsExactly("patient-identifier-a"))
                    .verifyComplete());

    assertThat(messages(events))
        .containsExactly("Ignoring 1 consents that reference no patient of the bundle");
  }

  /**
   * Patient b has only a consent with a malformed date, which grants no consent, so b is excluded
   * with a reason, while selection of a is not aborted.
   */
  @Test
  void consentWithMalformedDateExcludesPatient() {
    var b = consentFor("b", "MDAT_erheben");
    b.getProvision()
        .setProvision(List.of(provisionWithPlaceholderStart(POLICY_SYSTEM, "MDAT_erheben")));
    var bundle =
        Stream.of(patient("a"), patient("b"), consentFor("a", "MDAT_erheben"), b)
            .collect(toBundle());
    registerPage(fetchAllRequest(), bundle, c -> fhirResponse(encodeWithMalformedDates(c)));

    create(cohortSelector.selectCohort(List.of()).collectList())
        .assertNext(entries ->
            assertThat(entries)
                .satisfiesExactlyInAnyOrder(
                    a ->
                        assertThat(a)
                            .isInstanceOf(Consented.class)
                            .extracting(CohortEntry::identifier)
                            .isEqualTo("patient-identifier-a"),
                    excluded ->
                        assertThat(excluded)
                            .isEqualTo(
                                new Excluded(
                                    "patient-identifier-b",
                                    "Consent for policy MDAT_erheben has an unreadable"
                                        + " date"))))
        .verifyComplete();
  }

  @Test
  void groupingRunsOnInjectedScheduler() {
    var used = new AtomicBoolean(false);
    var delegate = Schedulers.parallel();
    Scheduler spy =
        new Scheduler() {
          @Override
          public Disposable schedule(Runnable task) {
            return delegate.schedule(task);
          }

          @Override
          public Worker createWorker() {
            used.set(true);
            return delegate.createWorker();
          }
        };
    var selector =
        new FhirCohortSelector(
            selectorConfig,
            fhirClient,
            consentedPatientExtractor(selectorConfig),
            new DefaultRetryStrategy(meterRegistry),
            spy,
            4);

    var bundle = cohortGenerator.generate();
    registerPage(fetchAllRequest(), bundle);

    create(selector.selectCohort(List.of())).expectNextCount(1).verifyComplete();
    assertThat(used).isTrue();
  }

  @Test
  void consentBundleForIdsSucceeds() {
    var bundle = cohortGenerator.generate();
    registerPage(fetchListRequest("patient-identifier-103291"), bundle);

    create(cohortSelector.selectCohort(List.of("patient-identifier-103291")))
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
  void paging() {
    var bundles = cohortGenerator.generate(2, 1, 1).toList();

    registerPage(fetchAllRequest(), bundles.getFirst());
    registerPage(nextPageRequest(), bundles.get(1));

    create(cohortSelector.selectCohort(List.of())).expectNextCount(2).verifyComplete();
  }

  /**
   * Patient x is a duplicate patient resource in the source, returned on both pages. The consent
   * search by identifier returns the consents and patient resources of both on each page.
   */
  @Test
  void slowConsumerDoesNotTimeOutPaging() {
    var pages = cohortGenerator.generate(5, 1, 1).toList();
    registerPage(fetchAllRequest(), pages.getFirst());
    range(1, pages.size())
        .forEach(i ->
            registerPage(
                get(urlPathEqualTo("/Patient")).withQueryParam("_page", equalTo(String.valueOf(i))),
                pages.get(i)));
    var selector =
        new FhirCohortSelector(
            selectorConfig,
            fhirClient,
            consentedPatientExtractor(selectorConfig),
            new DefaultRetryStrategy(meterRegistry),
            Schedulers.parallel(),
            4,
            ofMillis(500));

    create(selector.selectCohort(List.of()), 0)
        .thenRequest(1)
        .expectNextCount(1)
        .thenAwait(ofMillis(1500))
        .thenRequest(Long.MAX_VALUE)
        .expectNextCount(4)
        .verifyComplete();
  }

  @Test
  void patientOnTwoPagesIsSelectedOnce() {
    registerDuplicateOfXOnSecondPage(fetchAllRequest(), consentFor("x", "MDAT_erheben"));

    var events =
        recordLog(
            SelectedPatients.class,
            Level.WARN,
            () ->
                create(cohortSelector.selectCohort(List.of()))
                    .assertNext(e ->
                        assertThat(e)
                            .isInstanceOf(Consented.class)
                            .extracting(CohortEntry::identifier)
                            .isEqualTo("patient-identifier-x"))
                    .verifyComplete());

    assertThat(messages(events))
        .singleElement()
        .satisfies(
            m -> assertThat(m).contains("Ignoring 1 duplicate patients").doesNotContain("-x"));
  }

  @Test
  void duplicatesOnTwoPagesAreSelectedOnceByConsentsOfBoth() {
    registerDuplicateOfXOnSecondPage(fetchAllRequest(), consentFor("x", "MDAT_speichern"));

    create(selectorRequiring("MDAT_erheben", "MDAT_speichern").selectCohort(List.of()))
        .assertNext(e -> assertThat(e).isInstanceOf(Consented.class))
        .verifyComplete();
  }

  @Test
  void requestedDuplicatesOnTwoPagesAreSelectedOnceByConsentsOfBoth() {
    registerDuplicateOfXOnSecondPage(
        fetchListRequest("patient-identifier-x"), consentFor("x", "MDAT_speichern"));

    create(
            selectorRequiring("MDAT_erheben", "MDAT_speichern")
                .selectCohort(List.of("patient-identifier-x")))
        .assertNext(e -> assertThat(e).isInstanceOf(Consented.class))
        .verifyComplete();
  }

  /**
   * Not consented, the duplicate is classified again on the second page, the same way, so the
   * report lists it once.
   */
  @Test
  void excludedDuplicatesOnTwoPagesAreExcludedOnce() {
    var faulty = consentFor("x", "MDAT_speichern");
    faulty.getProvision().getProvisionFirstRep().setPeriod(null);
    registerDuplicateOfXOnSecondPage(fetchAllRequest(), faulty);

    var excluded =
        new Excluded("patient-identifier-x", "Consent for policy MDAT_speichern has no start date");
    create(selectorRequiring("MDAT_erheben", "MDAT_speichern").selectCohort(List.of()))
        .expectNext(excluded)
        .verifyComplete();
  }

  @Test
  void includedPatientNotOnPageIsMerged() {
    wireMock.register(
        fetchAllRequest().willReturn(fhirResponse(Stream.of(patient("x")).collect(toBundle()))));
    var consents =
        Stream.of(
                consentFor("x", "MDAT_erheben"),
                consentFor("x", "MDAT_speichern").setPatient(new Reference("Patient/patient-x2")),
                duplicateOfX())
            .collect(toBundle());
    consents.getEntry().getLast().getSearch().setMode(Bundle.SearchEntryMode.INCLUDE);
    wireMock.register(consentRequest("patient-identifier-x").willReturn(fhirResponse(consents)));

    create(selectorRequiring("MDAT_erheben", "MDAT_speichern").selectCohort(List.of()))
        .assertNext(e -> assertThat(e).isInstanceOf(Consented.class))
        .verifyComplete();
  }

  @Test
  void warningAboutPatientsAlreadySelectedIsLoggedOnCancel() {
    var firstPage = Stream.<Resource>of(patient("x")).collect(toBundle());
    firstPage.addLink().setRelation("next").setUrl("/Patient?_page=1");
    var secondPage = Stream.<Resource>of(duplicateOfX(), patient("y")).collect(toBundle());
    secondPage.addLink().setRelation("next").setUrl("/Patient?_page=2");
    wireMock.register(fetchAllRequest().willReturn(fhirResponse(firstPage)));
    wireMock.register(nextPageRequest().willReturn(fhirResponse(secondPage)));
    wireMock.register(
        get(urlPathEqualTo("/Patient"))
            .withQueryParam("_page", equalTo("2"))
            .willReturn(
                fhirResponse(Stream.<Resource>of().collect(toBundle())).withFixedDelay(5000)));
    var consentsOfX =
        Stream.of(consentFor("x", "MDAT_erheben"), patient("x"), duplicateOfX())
            .collect(toBundle());
    wireMock.register(consentRequest("patient-identifier-x").willReturn(fhirResponse(consentsOfX)));
    wireMock.register(
        consentRequest("patient-identifier-x", "patient-identifier-y")
            .willReturn(
                fhirResponse(
                    Stream.of(
                            consentFor("x", "MDAT_erheben"),
                            consentFor("y", "MDAT_erheben"),
                            patient("x"),
                            duplicateOfX())
                        .collect(toBundle()))));

    var events =
        recordLog(
            SelectedPatients.class,
            Level.WARN,
            () ->
                create(cohortSelector.selectCohort(List.of()))
                    .expectNextCount(2)
                    .thenCancel()
                    .verify());

    assertThat(messages(events))
        .singleElement()
        .asString()
        .contains("Ignoring 1 duplicate patients");
  }

  @Test
  void warningAboutPatientsAlreadySelectedIsLoggedOnError() {
    var secondPage = Stream.<Resource>of(duplicateOfX()).collect(toBundle());
    secondPage.addLink().setRelation("next").setUrl("/Patient?_page=2");
    var firstPage = Stream.<Resource>of(patient("x")).collect(toBundle());
    firstPage.addLink().setRelation("next").setUrl("/Patient?_page=1");
    wireMock.register(fetchAllRequest().willReturn(fhirResponse(firstPage)));
    wireMock.register(nextPageRequest().willReturn(fhirResponse(secondPage)));
    wireMock.register(
        get(urlPathEqualTo("/Patient"))
            .withQueryParam("_page", equalTo("2"))
            .willReturn(notFound()));
    var consents =
        Stream.of(consentFor("x", "MDAT_erheben"), patient("x"), duplicateOfX())
            .collect(toBundle());
    wireMock.register(consentRequest("patient-identifier-x").willReturn(fhirResponse(consents)));

    var events =
        recordLog(
            SelectedPatients.class,
            Level.WARN,
            () ->
                create(cohortSelector.selectCohort(List.of()))
                    .assertNext(e -> assertThat(e).isInstanceOf(Consented.class))
                    .expectError(TransferProcessException.class)
                    .verify());

    assertThat(messages(events))
        .singleElement()
        .asString()
        .contains("Ignoring 1 duplicate patients");
  }

  @Test
  void patientExcludedOnPageBeforeFailingPageIsEmittedBeforeError() {
    var faulty = consentFor("x", "MDAT_erheben");
    faulty.getProvision().getProvisionFirstRep().setPeriod(null);
    var firstPage = Stream.of(patient("x"), faulty).collect(toBundle());
    firstPage.addLink().setRelation("next").setUrl("/Patient?_page=1");
    registerPage(fetchAllRequest(), firstPage);
    wireMock.register(nextPageRequest().willReturn(notFound()));
    var slowGrouping = Schedulers.fromExecutor(delayedExecutor(500, MILLISECONDS));

    create(cohortSelectorOn(slowGrouping).selectCohort(List.of()))
        .expectNext(
            new Excluded(
                "patient-identifier-x", "Consent for policy MDAT_erheben has no start date"))
        .expectError(TransferProcessException.class)
        .verify();
  }

  @Test
  void patientsWithSameIdentifierOnOnePageAreClassifiedTogether() {
    var faulty = consentFor("x", "MDAT_erheben");
    faulty.getProvision().getProvisionFirstRep().setPeriod(null);
    var page =
        Stream.of(patient("x"), faulty, duplicateOfX(), consentForDuplicateOfX())
            .collect(toBundle());
    registerPage(fetchAllRequest(), page);

    create(cohortSelector.selectCohort(List.of()))
        .assertNext(e -> assertThat(e).isInstanceOf(Consented.class))
        .verifyComplete();
  }

  @Test
  void consentsOfPatientPageAreReadFromAllConsentPages() {
    var faulty = consentFor("x", "MDAT_erheben");
    faulty.getProvision().getProvisionFirstRep().setPeriod(null);
    var firstConsentPage = Stream.<Resource>of(faulty).collect(toBundle());
    firstConsentPage.addLink().setRelation("next").setUrl("/Consent?_page=1");
    var secondConsentPage =
        Stream.<Resource>of(consentFor("x", "MDAT_erheben")).collect(toBundle());
    var page = Stream.of(patient("x")).collect(toBundle());
    wireMock.register(fetchAllRequest().willReturn(fhirResponse(page)));
    wireMock.register(
        consentRequest("patient-identifier-x").willReturn(fhirResponse(firstConsentPage)));
    wireMock.register(
        get(urlPathEqualTo("/Consent"))
            .withQueryParam("_page", equalTo("1"))
            .willReturn(fhirResponse(secondConsentPage)));

    create(cohortSelector.selectCohort(List.of()))
        .assertNext(e -> assertThat(e).isInstanceOf(Consented.class))
        .verifyComplete();
  }

  @Test
  void consentRequestErrorFailsSelectionAfterEarlierPages() {
    var faulty = consentFor("x", "MDAT_erheben");
    faulty.getProvision().getProvisionFirstRep().setPeriod(null);
    var firstPage = Stream.of(patient("x"), faulty).collect(toBundle());
    firstPage.addLink().setRelation("next").setUrl("/Patient?_page=1");
    registerPage(fetchAllRequest(), firstPage);
    wireMock.register(
        nextPageRequest().willReturn(fhirResponse(Stream.of(patient("y")).collect(toBundle()))));
    wireMock.register(consentRequest("patient-identifier-y").willReturn(notFound()));

    create(cohortSelector.selectCohort(List.of()))
        .expectNext(
            new Excluded(
                "patient-identifier-x", "Consent for policy MDAT_erheben has no start date"))
        .expectErrorSatisfies(e ->
            assertThat(e)
                .isInstanceOf(TransferProcessException.class)
                .hasMessageContaining("FHIR server"))
        .verify();
  }

  @Test
  void pageWithoutPatientsSendsNoConsentRequest() {
    var emptyBundle = Stream.<Resource>of().collect(toBundle());
    wireMock.register(fetchAllRequest().willReturn(fhirResponse(emptyBundle)));

    create(cohortSelector.selectCohort(List.of())).verifyComplete();
    wireMock.verifyThat(0, getRequestedFor(urlPathEqualTo("/Consent")));
  }

  @Test
  void consentsAreRequestedOnlyForPatientsWithIdentifierOfConfiguredSystem() {
    var unidentified = patient("y");
    unidentified.getIdentifierFirstRep().setSystem("http://other-system");
    var page =
        Stream.of(patient("x"), consentFor("x", "MDAT_erheben"), unidentified).collect(toBundle());
    registerPage(fetchAllRequest(), page);

    create(cohortSelector.selectCohort(List.of()))
        .assertNext(e -> assertThat(e.identifier()).isEqualTo("patient-identifier-x"))
        .verifyComplete();
  }

  @Test
  void pageOnlyWithPatientsWithoutIdentifierOfConfiguredSystemSendsNoConsentRequest() {
    var unidentified = patient("y");
    unidentified.getIdentifierFirstRep().setSystem("http://other-system");
    var page = Stream.<Resource>of(unidentified).collect(toBundle());
    wireMock.register(fetchAllRequest().willReturn(fhirResponse(page)));

    create(cohortSelector.selectCohort(List.of())).verifyComplete();
    wireMock.verifyThat(0, getRequestedFor(urlPathEqualTo("/Consent")));
  }

  @Test
  void consentsOfPatientReturnedTwiceOnPageAreRequestedOnce() {
    var page =
        Stream.of(patient("x"), patient("x"), consentFor("x", "MDAT_erheben")).collect(toBundle());
    registerPage(fetchAllRequest(), page);

    create(cohortSelector.selectCohort(List.of()))
        .assertNext(e -> assertThat(e).isInstanceOf(Consented.class))
        .verifyComplete();
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
            .willReturn(fhirResponse(patientsOf(bundles.getFirst()))));

    wireMock.register(
        nextPageRequest()
            .inScenario("retry-scenario")
            .whenScenarioStateIs("first-retry")
            .willReturn(serverError())
            .willSetStateTo("second-retry"));

    wireMock.register(
        nextPageRequest()
            .inScenario("retry-scenario")
            .whenScenarioStateIs("second-retry")
            .willReturn(fhirResponse(patientsOf(bundles.get(1)))));
    bundles.forEach(this::registerConsentsOf);

    create(cohortSelector.selectCohort(List.of())).expectNextCount(2).verifyComplete();
  }

  /**
   * We encode manually here as it was not clear from the docs and issues if <code>
   * wiremock.withQueryParam()</code> really encodes values. Here we make sure that our
   * implementation encodes only once, as was the problem in #1062.
   */
  @Test
  void testUrlEncoding() {
    var bundle = cohortGenerator.generate();
    var query = "?identifier=" + PID_SYSTEM + "%7Cpatient-identifier-134622";
    registerPage(
        get(urlEqualTo("/Patient" + query))
            .withHeader(ACCEPT, equalTo(MockServerUtil.APPLICATION_FHIR_JSON)),
        bundle);

    create(cohortSelector.selectCohort(List.of("patient-identifier-134622")))
        .expectNextCount(1)
        .verifyComplete();
  }

  @Test
  void testAbsoluteUrl() {
    var bundles = cohortGenerator.generate(2, 1, 1).toList();
    bundles.forEach(b -> b.getLink().forEach(injectBaseUrl()));

    registerPage(fetchAllRequest(), bundles.getFirst());
    registerPage(nextPageRequest(), bundles.get(1));

    create(cohortSelector.selectCohort(List.of())).expectNextCount(2).verifyComplete();
  }

  private static Patient patient(String id) {
    return (Patient)
        new Patient()
            .addIdentifier(
                new Identifier().setSystem(PID_SYSTEM).setValue("patient-identifier-" + id))
            .setId("patient-" + id);
  }

  private static Consent consentFor(String patientId, String policy) {
    var provision =
        new Consent.ProvisionComponent()
            .addProvision(
                new Consent.ProvisionComponent()
                    .setType(Consent.ConsentProvisionType.PERMIT)
                    .setPeriod(new Period().setStart(new Date(0)).setEnd(new Date(1)))
                    .addCode(
                        new CodeableConcept()
                            .addCoding(new Coding().setSystem(POLICY_SYSTEM).setCode(policy))));
    return (Consent)
        new Consent()
            .setStatus(Consent.ConsentState.ACTIVE)
            .setProvision(provision)
            .setPatient(new Reference("Patient/patient-" + patientId))
            .setId("consent-" + patientId);
  }

  /**
   * Registers patient x on the first and its duplicate on the second page of {@code patientSearch},
   * and the consent search for x, returning both patients, a consent of x for MDAT_erheben and
   * {@code consentOfDuplicate}.
   */
  private void registerDuplicateOfXOnSecondPage(
      MappingBuilder patientSearch, Consent consentOfDuplicate) {
    var firstPage = Stream.<Resource>of(patient("x")).collect(toBundle());
    firstPage.addLink().setRelation("next").setUrl("/Patient?_page=1");
    var secondPage = Stream.<Resource>of(duplicateOfX()).collect(toBundle());
    wireMock.register(patientSearch.willReturn(fhirResponse(firstPage)));
    wireMock.register(nextPageRequest().willReturn(fhirResponse(secondPage)));
    consentOfDuplicate.setPatient(new Reference("Patient/patient-x2"));
    var consents =
        Stream.of(consentFor("x", "MDAT_erheben"), consentOfDuplicate, patient("x"), duplicateOfX())
            .collect(toBundle());
    wireMock.register(consentRequest("patient-identifier-x").willReturn(fhirResponse(consents)));
  }

  private FhirCohortSelector selectorRequiring(String... policies) {
    var requiring =
        new FhirCohortSelectorConfig(
            new HttpClientConfig("ignored"), PID_SYSTEM, POLICY_SYSTEM, Set.of(policies));
    return new FhirCohortSelector(
        requiring,
        fhirClient,
        consentedPatientExtractor(requiring),
        new DefaultRetryStrategy(meterRegistry),
        Schedulers.parallel(),
        4);
  }

  /** Another patient resource with the identifier of patient x. */
  private static Patient duplicateOfX() {
    return (Patient) patient("x").setId("patient-x2");
  }

  private static Consent consentForDuplicateOfX() {
    return consentFor("x", "MDAT_erheben").setPatient(new Reference("Patient/patient-x2"));
  }

  private Consumer<BundleLinkComponent> injectBaseUrl() {
    return l -> l.setUrl(config.baseUrl() + l.getUrl());
  }

  /**
   * An absolute next link must be fetched as it is, and not be resolved against the base URL. The
   * second page lives on another server, so only a request to that server finds it.
   */
  @Test
  void absoluteNextLinkIsFetchedFromItsOwnHost() {
    var otherServer = onRandomPort();
    try {
      var bundles = cohortGenerator.generate(2, 1, 1).toList();
      bundles.forEach(b -> b.getLink().forEach(l -> l.setUrl(otherServer.baseUrl() + l.getUrl())));

      registerPage(fetchAllRequest(), bundles.getFirst());
      new WireMock(otherServer.port())
          .register(nextPageRequest().willReturn(fhirResponse(patientsOf(bundles.get(1)))));
      registerConsentsOf(bundles.get(1));

      create(cohortSelector.selectCohort(List.of())).expectNextCount(2).verifyComplete();
    } finally {
      otherServer.stop();
    }
  }
}
