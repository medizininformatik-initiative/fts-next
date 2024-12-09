package care.smith.fts.tca.consent;

import static care.smith.fts.test.FhirGenerators.randomUuid;
import static care.smith.fts.util.FhirUtils.toBundle;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.matchers.MatchType.ONLY_MATCHING_FIELDS;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.APPLICATION_JSON;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;
import static reactor.test.StepVerifier.create;

import care.smith.fts.tca.consent.ConsentedPatientsProvider.PagingParams;
import care.smith.fts.test.FhirGenerator;
import care.smith.fts.test.FhirGenerators;
import care.smith.fts.test.TestWebClientFactory;
import care.smith.fts.util.FhirUtils;
import care.smith.fts.util.error.UnknownDomainException;
import care.smith.fts.util.tca.ConsentFetchAllRequest;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.JsonBody;
import org.mockserver.model.Parameter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@SpringBootTest
@ExtendWith(MockServerExtension.class)
@Import(TestWebClientFactory.class)
class FhirConsentedPatientsProviderFetchAllTest {
  @Autowired WebClient.Builder httpClientBuilder;
  @Autowired MeterRegistry meterRegistry;

  @MockitoBean
  RedissonClient redisClient; // We need to mock the redisClient otherwise the tests won't start

  private static final String POLICY_SYSTEM =
      "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy";
  private FhirConsentedPatientsProvider fhirConsentProvider;

  @Qualifier("defaultPageSize")
  @Autowired
  private int defaultPageSize;

  private static final Set<String> POLICIES =
      Set.of(
          "IDAT_erheben",
          "IDAT_speichern_verarbeiten",
          "MDAT_erheben",
          "MDAT_speichern_verarbeiten");
  private static final ConsentFetchAllRequest CONSENT_FETCH_ALL_REQUEST =
      new ConsentFetchAllRequest("MII", POLICIES, POLICY_SYSTEM);
  private static String address;
  private static FhirGenerator<Bundle> gicsConsentGenerator;
  private static JsonBody jsonBody;

  @BeforeAll
  static void setUp(MockServerClient mockServer) throws IOException {
    address = "http://localhost:%d".formatted(mockServer.getPort());
    gicsConsentGenerator = FhirGenerators.gicsResponse(randomUuid(), randomUuid());
    jsonBody =
        json(
            """
                  {
                   "resourceType": "Parameters",
                   "parameter": [{"name": "domain", "valueString": "MII"}]
                  }
                  """,
            ONLY_MATCHING_FIELDS);
  }

  @AfterEach
  void tearDown(MockServerClient mockServer) {
    mockServer.reset();
  }

  @Test
  void paging(MockServerClient mockServer) {
    int totalEntries = 2 * defaultPageSize;
    fhirConsentProvider =
        new FhirConsentedPatientsProvider(
            httpClientBuilder.baseUrl(address).build(), meterRegistry);

    Bundle bundle =
        gicsConsentGenerator
            .generateResources()
            .limit(defaultPageSize)
            .collect(toBundle())
            .setTotal(totalEntries);

    HttpRequest postRequest =
        request().withMethod("POST").withPath("/$allConsentsForDomain").withBody(jsonBody);
    HttpResponse httpResponse =
        response().withBody(FhirUtils.fhirResourceToString(bundle), APPLICATION_JSON);
    mockServer
        .when(
            postRequest.withQueryStringParameters(
                List.of(
                    new Parameter("_offset", "0"),
                    new Parameter("_count", String.valueOf(defaultPageSize)))))
        .respond(httpResponse);
    mockServer
        .when(
            postRequest.withQueryStringParameters(
                List.of(
                    new Parameter("_offset", String.valueOf(defaultPageSize)),
                    new Parameter("_count", String.valueOf(defaultPageSize)))))
        .respond(httpResponse);

    var expectedNextLink =
        "http://localhost:8080/api/v2/cd/consented-patients/fetch-all?from=%s&count=%s"
            .formatted(defaultPageSize, defaultPageSize);

    log.info("Get first page");
    create(
            fhirConsentProvider.fetchAll(
                CONSENT_FETCH_ALL_REQUEST,
                fromUriString("http://localhost:8080"),
                new PagingParams(0, defaultPageSize)))
        .assertNext(
            consentBundle ->
                assertThat(consentBundle.getLink("next").getUrl()).isEqualTo(expectedNextLink))
        .verifyComplete();
    log.info("Get second page");
    create(
            fhirConsentProvider.fetchAll(
                CONSENT_FETCH_ALL_REQUEST,
                fromUriString("http://localhost:8080"),
                new PagingParams(defaultPageSize, defaultPageSize)))
        .assertNext(consentBundle -> assertThat(consentBundle.getLink()).isEmpty())
        .verifyComplete();
  }

  @Test
  void noNextLinkOnLastPage(MockServerClient mockServer) {
    int totalEntries = 1;
    int pageSize = 1;

    fhirConsentProvider =
        new FhirConsentedPatientsProvider(
            httpClientBuilder.baseUrl(address).build(), meterRegistry);

    Bundle bundle =
        Stream.generate(gicsConsentGenerator::generateString)
            .limit(totalEntries)
            .map(FhirUtils::stringToFhirBundle)
            .collect(toBundle())
            .setTotal(totalEntries);

    HttpRequest postRequest =
        request().withMethod("POST").withPath("/$allConsentsForDomain").withBody(jsonBody);
    HttpResponse httpResponse =
        response().withBody(FhirUtils.fhirResourceToString(bundle), APPLICATION_JSON);
    mockServer
        .when(
            postRequest.withQueryStringParameters(
                List.of(
                    new Parameter("_offset", "0"),
                    new Parameter("_count", String.valueOf(pageSize)))))
        .respond(httpResponse);

    create(
            fhirConsentProvider.fetchAll(
                CONSENT_FETCH_ALL_REQUEST,
                fromUriString("http://trustcenteragent:1234"),
                new PagingParams(0, pageSize)))
        .assertNext(consentBundle -> assertThat(consentBundle.getLink("next")).isNull())
        .verifyComplete();
  }

  @Test
  void noConsents(MockServerClient mockServer) {
    int totalEntries = 0;
    int pageSize = 1;

    fhirConsentProvider =
        new FhirConsentedPatientsProvider(
            httpClientBuilder.baseUrl(address).build(), meterRegistry);
    Bundle bundle =
        Stream.generate(gicsConsentGenerator::generateString)
            .limit(totalEntries)
            .map(FhirUtils::stringToFhirBundle)
            .collect(toBundle())
            .setTotal(totalEntries);

    HttpRequest postRequest =
        request().withMethod("POST").withPath("/$allConsentsForDomain").withBody(jsonBody);
    HttpResponse httpResponse =
        response().withBody(FhirUtils.fhirResourceToString(bundle), APPLICATION_JSON);
    mockServer
        .when(
            postRequest.withQueryStringParameters(
                List.of(
                    new Parameter("_offset", "0"),
                    new Parameter("_count", String.valueOf(pageSize)))))
        .respond(httpResponse);

    create(
            fhirConsentProvider.fetchAll(
                CONSENT_FETCH_ALL_REQUEST,
                fromUriString("http://trustcenteragent:1234"),
                new PagingParams(0, pageSize)))
        .assertNext(
            consentBundle -> {
              assertThat(consentBundle.getEntry()).isEmpty();
              assertThat(consentBundle.getLink("next")).isNull();
            })
        .verifyComplete();
  }

  @Test
  void unknownDomainCausesGicsNotFound(MockServerClient mockServer) {
    int pageSize = 2;

    fhirConsentProvider =
        new FhirConsentedPatientsProvider(
            httpClientBuilder.baseUrl(address).build(), meterRegistry);

    var operationOutcome = new OperationOutcome();
    var issue = operationOutcome.addIssue().setSeverity(IssueSeverity.ERROR);
    issue.setDiagnostics("No consents found for domain");

    var postRequest =
        request().withMethod("POST").withPath("/$allConsentsForDomain").withBody(jsonBody);
    var httpResponse =
        response()
            .withStatusCode(404)
            .withBody(FhirUtils.fhirResourceToString(operationOutcome), APPLICATION_JSON);
    mockServer.when(postRequest).respond(httpResponse);

    create(
            fhirConsentProvider.fetchAll(
                CONSENT_FETCH_ALL_REQUEST,
                fromUriString("http://trustcenteragent:1234"),
                new PagingParams(0, pageSize)))
        .expectError(UnknownDomainException.class)
        .verify();
  }

  @Test
  void somethingElseCausesGicsNotFound(MockServerClient mockServer) {
    int pageSize = 2;

    fhirConsentProvider =
        new FhirConsentedPatientsProvider(
            httpClientBuilder.baseUrl(address).build(), meterRegistry);

    var operationOutcome = new OperationOutcome();
    var issue = operationOutcome.addIssue().setSeverity(IssueSeverity.ERROR);
    issue.setDiagnostics("Something's not right");

    var postRequest =
        request().withMethod("POST").withPath("/$allConsentsForDomain").withBody(jsonBody);
    var httpResponse =
        response()
            .withStatusCode(404)
            .withBody(FhirUtils.fhirResourceToString(operationOutcome), APPLICATION_JSON);
    mockServer.when(postRequest).respond(httpResponse);

    create(
            fhirConsentProvider.fetchAll(
                CONSENT_FETCH_ALL_REQUEST,
                fromUriString("http://trustcenteragent:1234"),
                new PagingParams(0, pageSize)))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void diagnosticsIsNullInHandleGicsNotFound(MockServerClient mockServer) {
    int pageSize = 2;

    fhirConsentProvider =
        new FhirConsentedPatientsProvider(
            httpClientBuilder.baseUrl(address).build(), meterRegistry);

    var operationOutcome = new OperationOutcome();
    operationOutcome.addIssue().setSeverity(IssueSeverity.ERROR);

    var postRequest =
        request().withMethod("POST").withPath("/$allConsentsForDomain").withBody(jsonBody);
    var httpResponse =
        response()
            .withStatusCode(404)
            .withBody(FhirUtils.fhirResourceToString(operationOutcome), APPLICATION_JSON);
    mockServer.when(postRequest).respond(httpResponse);

    create(
            fhirConsentProvider.fetchAll(
                CONSENT_FETCH_ALL_REQUEST,
                fromUriString("http://trustcenteragent:1234"),
                new PagingParams(0, pageSize)))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void emptyPoliciesYieldEmptyBundle(MockServerClient mockServer) {
    int totalEntries = 0;
    int pageSize = 2;

    var consentRequest = new ConsentFetchAllRequest("MII", Set.of(), POLICY_SYSTEM);
    fhirConsentProvider =
        new FhirConsentedPatientsProvider(
            httpClientBuilder.baseUrl(address).build(), meterRegistry);
    Bundle bundle =
        Stream.generate(gicsConsentGenerator::generateString)
            .limit(totalEntries)
            .map(FhirUtils::stringToFhirBundle)
            .collect(toBundle())
            .setTotal(totalEntries);

    HttpRequest postRequest =
        request().withMethod("POST").withPath("/$allConsentsForDomain").withBody(jsonBody);
    HttpResponse httpResponse =
        response().withBody(FhirUtils.fhirResourceToString(bundle), APPLICATION_JSON);
    mockServer.when(postRequest).respond(httpResponse);

    create(
            fhirConsentProvider.fetchAll(
                consentRequest,
                fromUriString("http://trustcenteragent:8080"),
                new PagingParams(0, pageSize)))
        .assertNext(
            consentBundle -> {
              assertThat(consentBundle.getEntry()).isEmpty();
            })
        .verifyComplete();
  }
}
