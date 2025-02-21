package care.smith.fts.tca.consent;

import static care.smith.fts.test.FhirGenerators.randomUuid;
import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static care.smith.fts.util.FhirUtils.toBundle;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static java.lang.String.valueOf;
import static java.util.Map.entry;
import static java.util.Map.ofEntries;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;
import static reactor.test.StepVerifier.create;

import care.smith.fts.tca.consent.ConsentedPatientsProvider.PagingParams;
import care.smith.fts.test.FhirGenerator;
import care.smith.fts.test.FhirGenerators;
import care.smith.fts.test.TestWebClientFactory;
import care.smith.fts.util.FhirUtils;
import care.smith.fts.util.tca.ConsentFetchAllRequest;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@SpringBootTest
@WireMockTest
@Import(TestWebClientFactory.class)
class FhirConsentedPatientsProviderFetchAllIT {

  @Autowired WebClient.Builder httpClientBuilder;
  @Autowired MeterRegistry meterRegistry;

  @MockitoBean
  RedissonClient redisClient; // We need to mock the redisClient otherwise the tests won't start

  private static final String POLICY_SYSTEM =
      "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy";

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
  private String address;
  private FhirGenerator<Bundle> gicsConsentGenerator;
  private static final String jsonBody =
      """
  {
   "resourceType": "Parameters",
   "parameter": [{"name": "domain", "valueString": "MII"}]
  }
  """;
  private static WireMock wireMock;
  private FhirConsentedPatientsProvider fhirConsentProvider;

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntime) throws IOException {
    address = wireMockRuntime.getHttpBaseUrl();
    wireMock = wireMockRuntime.getWireMock();
    gicsConsentGenerator = FhirGenerators.gicsResponse(randomUuid(), randomUuid());
    fhirConsentProvider =
        new FhirConsentedPatientsProvider(
            httpClientBuilder.baseUrl(address).build(), meterRegistry);
  }

  @AfterEach
  void tearDown() {
    wireMock.resetMappings();
  }

  @Test
  void paging() {
    int totalEntries = 2 * defaultPageSize;

    var bundle =
        gicsConsentGenerator
            .generateResources()
            .limit(defaultPageSize)
            .collect(toBundle())
            .setTotal(totalEntries);

    register(0, defaultPageSize, fhirResponse(bundle));
    register(defaultPageSize, defaultPageSize, fhirResponse(bundle));

    var expectedNextLink =
        "http://localhost:8080/api/v2/cd/consented-patients/fetch-all?from=%s&count=%s"
            .formatted(defaultPageSize, defaultPageSize);

    log.info("Get first page");
    create(gicsRequest(CONSENT_FETCH_ALL_REQUEST, "http://localhost:8080", 0, defaultPageSize))
        .assertNext(
            consentBundle ->
                assertThat(consentBundle.getLink("next").getUrl()).isEqualTo(expectedNextLink))
        .verifyComplete();
    log.info("Get second page");
    create(
            gicsRequest(
                CONSENT_FETCH_ALL_REQUEST,
                "http://localhost:8080",
                defaultPageSize,
                defaultPageSize))
        .assertNext(consentBundle -> assertThat(consentBundle.getLink()).isEmpty())
        .verifyComplete();
  }

  private void register(int number, int defaultPageSize, ResponseDefinitionBuilder bundle) {
    wireMock.register(
        post(urlPathEqualTo("/$allConsentsForDomain"))
            .withRequestBody(equalToJson(jsonBody))
            .withQueryParams(
                ofEntries(
                    entry("_offset", equalTo(valueOf(number))),
                    entry("_count", equalTo(valueOf(defaultPageSize)))))
            .willReturn(bundle));
  }

  @Test
  void noNextLinkOnLastPage() {
    int totalEntries = 1;
    int pageSize = 1;

    var bundle = consentBundle(totalEntries);

    register(0, pageSize, fhirResponse(bundle));

    create(gicsRequest(CONSENT_FETCH_ALL_REQUEST, "http://trustcenteragent:1234", 0, pageSize))
        .assertNext(consentBundle -> assertThat(consentBundle.getLink("next")).isNull())
        .verifyComplete();
  }

  private Mono<Bundle> gicsRequest(
      ConsentFetchAllRequest consentFetchAllRequest, String url, int from, int pageSize) {
    return fhirConsentProvider.fetchAll(
        consentFetchAllRequest, fromUriString(url), new PagingParams(from, pageSize));
  }

  @Test
  void noConsents() {
    int totalEntries = 0;
    int pageSize = 1;

    var bundle = consentBundle(totalEntries);

    register(0, pageSize, fhirResponse(bundle));

    create(gicsRequest(CONSENT_FETCH_ALL_REQUEST, "http://trustcenteragent:1234", 0, pageSize))
        .assertNext(
            consentBundle -> {
              assertThat(consentBundle.getEntry()).isEmpty();
              assertThat(consentBundle.getLink("next")).isNull();
            })
        .verifyComplete();
  }

  @Test
  void handleGicsErrorResponse() {
    int pageSize = 2;

    var operationOutcome = new OperationOutcome();
    var issue = operationOutcome.addIssue().setSeverity(IssueSeverity.ERROR);
    issue.setDiagnostics("No consents found for domain");

    register(0, pageSize, fhirResponse(operationOutcome, NOT_FOUND));

    create(gicsRequest(CONSENT_FETCH_ALL_REQUEST, "http://trustcenteragent:1234", 0, pageSize))
        .expectError(GicsResponseException.class)
        .verify();
  }

  @Test
  void diagnosticsIsNullInHandleGicsError() {
    int pageSize = 2;

    var operationOutcome = new OperationOutcome();
    operationOutcome.addIssue().setSeverity(IssueSeverity.ERROR);

    register(0, pageSize, fhirResponse(operationOutcome, NOT_FOUND));

    create(gicsRequest(CONSENT_FETCH_ALL_REQUEST, "http://trustcenteragent:1234", 0, pageSize))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void emptyPoliciesYieldEmptyBundle() {
    int totalEntries = 0;
    int pageSize = 2;

    var consentRequest = new ConsentFetchAllRequest("MII", Set.of(), POLICY_SYSTEM);

    var bundle = consentBundle(totalEntries);

    register(0, pageSize, fhirResponse(bundle));

    create(gicsRequest(consentRequest, "http://trustcenteragent:8080", 0, pageSize))
        .assertNext(
            consentBundle -> {
              assertThat(consentBundle.getEntry()).isEmpty();
            })
        .verifyComplete();
  }

  private Bundle consentBundle(int totalEntries) {
    return Stream.generate(gicsConsentGenerator::generateString)
        .limit(totalEntries)
        .map(FhirUtils::stringToFhirBundle)
        .collect(toBundle())
        .setTotal(totalEntries);
  }
}
