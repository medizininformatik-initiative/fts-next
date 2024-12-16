package care.smith.fts.tca.consent;

import static care.smith.fts.test.FhirGenerators.fromList;
import static care.smith.fts.test.FhirGenerators.randomUuid;
import static care.smith.fts.util.FhirUtils.toBundle;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;
import static reactor.test.StepVerifier.create;

import care.smith.fts.tca.consent.ConsentedPatientsProvider.PagingParams;
import care.smith.fts.test.FhirGenerator;
import care.smith.fts.test.FhirGenerators;
import care.smith.fts.test.TestWebClientFactory;
import care.smith.fts.util.FhirUtils;
import care.smith.fts.util.error.UnknownDomainException;
import care.smith.fts.util.tca.ConsentFetchRequest;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@SpringBootTest
@WireMockTest
@Import(TestWebClientFactory.class)
class FhirConsentedPatientsProviderFetchTest {

  @Autowired WebClient.Builder httpClientBuilder;
  @Autowired MeterRegistry meterRegistry;

  @MockitoBean
  RedissonClient redisClient; // We need to mock the redisClient otherwise the tests won't start

  private static final String POLICY_SYSTEM =
      "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy";
  private static final String PATIENT_IDENTIFIER_SYSTEM =
      "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym";
  private FhirConsentedPatientsProvider fhirConsentProvider;

  private static final Set<String> POLICIES =
      Set.of(
          "IDAT_erheben",
          "IDAT_speichern_verarbeiten",
          "MDAT_erheben",
          "MDAT_speichern_verarbeiten");
  private static final ConsentFetchRequest consentRequest =
      new ConsentFetchRequest(
          "MII",
          POLICIES,
          POLICY_SYSTEM,
          PATIENT_IDENTIFIER_SYSTEM,
          List.of("id1", "id2", "id3", "id4"));

  private static final String jsonBody1 =
      """
            {
              "resourceType": "Parameters",
              "parameter": [
                {"name": "domain", "valueString": "MII"},
                {"name":  "personIdentifier", "valueIdentifier":  {"system":  "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym", "value": "id1"}},
                {"name":  "personIdentifier", "valueIdentifier":  {"system":  "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym", "value": "id2"}}
            ]}
            """;

  private static String address;
  private static WireMock wireMock;
  private static FhirGenerator<Bundle> gicsConsentGenerator;

  @BeforeAll
  static void setUp(WireMockRuntimeInfo wireMockRuntime) throws IOException {
    address = wireMockRuntime.getHttpBaseUrl();
    wireMock = wireMockRuntime.getWireMock();
    gicsConsentGenerator =
        FhirGenerators.gicsResponse(randomUuid(), fromList(List.of("id1", "id2", "id3", "id4")));
  }

  @AfterEach
  void tearDown() {
    wireMock.resetMappings();
  }

  @Test
  void paging() {
    int pageSize = 2;
    int totalEntries = 2 * pageSize;

    fhirConsentProvider =
        new FhirConsentedPatientsProvider(
            httpClientBuilder.baseUrl(address).build(), meterRegistry);

    var bundle1 =
        gicsConsentGenerator
            .generateResources()
            .limit(pageSize)
            .collect(toBundle())
            .setTotal(totalEntries);
    var bundle2 =
        gicsConsentGenerator
            .generateResources()
            .limit(pageSize)
            .collect(toBundle())
            .setTotal(totalEntries);

    wireMock.register(
        post("/$allConsentsForPerson")
            .withRequestBody(equalToJson(jsonBody1))
            .willReturn(jsonResponse(FhirUtils.fhirResourceToString(bundle1), 200)));

    String jsonBody2 = """
        {
          "resourceType": "Parameters",
          "parameter": [
            {"name": "domain", "valueString": "MII"},
            {"name":  "personIdentifier", "valueIdentifier":  {"system":  "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym", "value": "id3"}},
            {"name":  "personIdentifier", "valueIdentifier":  {"system":  "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym", "value": "id4"}}
        ]}
        """;
    wireMock.register(
        post("/$allConsentsForPerson")
            .withRequestBody(equalToJson(jsonBody2))
            .willReturn(jsonResponse(FhirUtils.fhirResourceToString(bundle2), 200)));

    var expectedNextLink =
        "http://trustcenteragent:8080/api/v2/cd/consented-patients/fetch?from=%s&count=%s"
            .formatted(pageSize, pageSize);

    log.info("Get first page");
    create(
            fhirConsentProvider.fetch(
                consentRequest,
                fromUriString("http://trustcenteragent:8080"),
                new PagingParams(0, pageSize)))
        .assertNext(
            consentBundle ->
                assertThat(consentBundle.getLink("next").getUrl()).isEqualTo(expectedNextLink))
        .verifyComplete();
    log.info("Get second page");
    create(
            fhirConsentProvider.fetch(
                consentRequest,
                fromUriString("http://trustcenteragent:8080"),
                new PagingParams(pageSize, pageSize)))
        .assertNext(consentBundle -> assertThat(consentBundle.getLink()).isEmpty())
        .verifyComplete();
  }

  @Test
  void noConsents() {
    int totalEntries = 0;
    int pageSize = 2;

    fhirConsentProvider =
        new FhirConsentedPatientsProvider(
            httpClientBuilder.baseUrl(address).build(), meterRegistry);
    Bundle bundle =
        Stream.generate(gicsConsentGenerator::generateString)
            .limit(totalEntries)
            .map(FhirUtils::stringToFhirBundle)
            .collect(toBundle())
            .setTotal(totalEntries);

    wireMock.register(
        post("/$allConsentsForPerson")
            .withRequestBody(equalToJson(jsonBody1))
            .willReturn(jsonResponse(FhirUtils.fhirResourceToString(bundle), 200)));

    var expectedNextLink =
        "http://trustcenteragent:8080/api/v2/cd/consented-patients/fetch?from=%s&count=%s"
            .formatted(pageSize, pageSize);
    create(
            fhirConsentProvider.fetch(
                consentRequest,
                fromUriString("http://trustcenteragent:8080"),
                new PagingParams(0, pageSize)))
        .assertNext(
            consentBundle -> {
              assertThat(consentBundle.getEntry()).isEmpty();
              assertThat(consentBundle.getLink("next").getUrl()).isEqualTo(expectedNextLink);
            })
        .verifyComplete();
  }

  @Test
  void unknownDomainCausesGicsNotFound() {
    int pageSize = 2;

    fhirConsentProvider =
        new FhirConsentedPatientsProvider(
            httpClientBuilder.baseUrl(address).build(), meterRegistry);

    var operationOutcome = new OperationOutcome();
    var issue = operationOutcome.addIssue().setSeverity(IssueSeverity.ERROR);
    issue.setDiagnostics("No consents found for domain");

    wireMock.register(
        post("/$allConsentsForPerson")
            .withRequestBody(equalToJson(jsonBody1, true, true))
            .willReturn(jsonResponse(FhirUtils.fhirResourceToString(operationOutcome), 404)));

    create(
            fhirConsentProvider.fetch(
                consentRequest,
                fromUriString("http://trustcenteragent:1234"),
                new PagingParams(0, pageSize)))
        .expectError(UnknownDomainException.class)
        .verify();
  }

  @Test
  void somethingElseCausesGicsNotFound() {
    int pageSize = 2;

    fhirConsentProvider =
        new FhirConsentedPatientsProvider(
            httpClientBuilder.baseUrl(address).build(), meterRegistry);

    var operationOutcome = new OperationOutcome();
    var issue = operationOutcome.addIssue().setSeverity(IssueSeverity.ERROR);
    issue.setDiagnostics("Something's not right");

    wireMock.register(
        post("/$allConsentsForPerson")
            .withRequestBody(equalToJson(jsonBody1, true, true))
            .willReturn(jsonResponse(FhirUtils.fhirResourceToString(operationOutcome), 404)));

    create(
            fhirConsentProvider.fetch(
                consentRequest,
                fromUriString("http://trustcenteragent:1234"),
                new PagingParams(0, pageSize)))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void diagnosticsIsNullInHandleGicsNotFound() {
    int pageSize = 2;

    fhirConsentProvider =
        new FhirConsentedPatientsProvider(
            httpClientBuilder.baseUrl(address).build(), meterRegistry);

    var operationOutcome = new OperationOutcome();
    operationOutcome.addIssue().setSeverity(IssueSeverity.ERROR);

    wireMock.register(
        post("/$allConsentsForPerson")
            .withRequestBody(equalToJson(jsonBody1, true, true))
            .willReturn(jsonResponse(FhirUtils.fhirResourceToString(operationOutcome), 404)));

    create(
            fhirConsentProvider.fetch(
                consentRequest,
                fromUriString("http://trustcenteragent:1234"),
                new PagingParams(0, pageSize)))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void emptyPoliciesYieldEmptyBundle() {
    var consentRequest =
        new ConsentFetchRequest(
            "MII",
            Set.of(),
            POLICY_SYSTEM,
            PATIENT_IDENTIFIER_SYSTEM,
            List.of("id1", "id2", "id3", "id4"));
    fhirConsentProvider =
        new FhirConsentedPatientsProvider(
            httpClientBuilder.baseUrl(address).build(), meterRegistry);

    create(
            fhirConsentProvider.fetch(
                consentRequest,
                fromUriString("http://trustcenteragent:8080"),
                new PagingParams(0, 2)))
        .assertNext(
            consentBundle -> {
              assertThat(consentBundle.getEntry()).isEmpty();
            })
        .verifyComplete();
  }

  @Test
  void emptyPidsYieldEmptyBundle() {
    ConsentFetchRequest consentRequest =
        new ConsentFetchRequest(
            "MII", POLICIES, POLICY_SYSTEM, PATIENT_IDENTIFIER_SYSTEM, List.of());
    fhirConsentProvider =
        new FhirConsentedPatientsProvider(
            httpClientBuilder.baseUrl(address).build(), meterRegistry);
    create(
            fhirConsentProvider.fetch(
                consentRequest,
                fromUriString("http://trustcenteragent:8080"),
                new PagingParams(0, 2)))
        .assertNext(
            consentBundle -> {
              assertThat(consentBundle.getEntry()).isEmpty();
            })
        .verifyComplete();
  }
}
