package care.smith.fts.tca.consent;

import static care.smith.fts.test.FhirGenerators.fromList;
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
import care.smith.fts.util.tca.ConsentRequest;
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
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@SpringBootTest
@ExtendWith(MockServerExtension.class)
@Import(TestWebClientFactory.class)
class FhirConsentedPatientsProviderFetchTest {
  @Autowired WebClient.Builder httpClientBuilder;
  @Autowired PolicyHandler policyHandler;
  @Autowired MeterRegistry meterRegistry;

  @MockBean
  RedissonClient redisClient; // We need to mock the redisClient otherwise the tests won't start

  private static final String POLICY_SYSTEM =
      "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy";
  private FhirConsentedPatientsProvider fhirConsentProvider;

  private static final Set<String> POLICIES =
      Set.of(
          "IDAT_erheben",
          "IDAT_speichern_verarbeiten",
          "MDAT_erheben",
          "MDAT_speichern_verarbeiten");
  private static final ConsentRequest consentRequest =
      new ConsentRequest("MII", POLICIES, POLICY_SYSTEM, List.of("id1", "id2", "id3", "id4"));
  private static String address;
  private static FhirGenerator<Bundle> gicsConsentGenerator;
  private static JsonBody jsonBody1;
  private static JsonBody jsonBody2;

  @BeforeAll
  static void setUp(MockServerClient mockServer) throws IOException {
    address = "http://localhost:%d".formatted(mockServer.getPort());
    gicsConsentGenerator =
        FhirGenerators.gicsResponse(randomUuid(), fromList(List.of("id1", "id2", "id3", "id4")));

    jsonBody1 =
        json(
            """
                  {
                    "resourceType": "Parameters",
                    "parameter": [
                      {"name": "domain", "valueString": "MII"},
                      {"name":  "personIdentifier", "valueIdentifier":  {"system":  "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy", "value": "id1"}},
                      {"name":  "personIdentifier", "valueIdentifier":  {"system":  "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy", "value": "id2"}}
                  ]}
                  """,
            ONLY_MATCHING_FIELDS);
    jsonBody2 =
        json(
            """
                  {
                    "resourceType": "Parameters",
                    "parameter": [
                      {"name": "domain", "valueString": "MII"},
                      {"name":  "personIdentifier", "valueIdentifier":  {"system":  "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy", "value": "id3"}},
                      {"name":  "personIdentifier", "valueIdentifier":  {"system":  "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy", "value": "id4"}}
                  ]}
                  """,
            ONLY_MATCHING_FIELDS);
  }

  @AfterEach
  void tearDown(MockServerClient mockServer) {
    mockServer.reset();
  }

  @Test
  void paging(MockServerClient mockServer) {
    int pageSize = 2;
    int totalEntries = 2 * pageSize;

    fhirConsentProvider =
        new FhirConsentedPatientsProvider(
            httpClientBuilder.baseUrl(address).build(), policyHandler, meterRegistry);

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

    var postRequest1 =
        request().withMethod("POST").withPath("/$allConsentsForPerson").withBody(jsonBody1);

    var httpResponse1 =
        response().withBody(FhirUtils.fhirResourceToString(bundle1), APPLICATION_JSON);
    mockServer.when(postRequest1).respond(httpResponse1);

    var postRequest2 =
        request().withMethod("POST").withPath("/$allConsentsForPerson").withBody(jsonBody2);

    var httpResponse2 =
        response().withBody(FhirUtils.fhirResourceToString(bundle2), APPLICATION_JSON);
    mockServer.when(postRequest2).respond(httpResponse2);

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
  void noConsents(MockServerClient mockServer) {
    int totalEntries = 0;
    int pageSize = 2;

    fhirConsentProvider =
        new FhirConsentedPatientsProvider(
            httpClientBuilder.baseUrl(address).build(), policyHandler, meterRegistry);
    Bundle bundle =
        Stream.generate(gicsConsentGenerator::generateString)
            .limit(totalEntries)
            .map(FhirUtils::stringToFhirBundle)
            .collect(toBundle())
            .setTotal(totalEntries);

    HttpRequest postRequest =
        request().withMethod("POST").withPath("/$allConsentsForPerson").withBody(jsonBody1);
    HttpResponse httpResponse =
        response().withBody(FhirUtils.fhirResourceToString(bundle), APPLICATION_JSON);
    mockServer.when(postRequest).respond(httpResponse);

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
  void unknownDomainCausesGicsNotFound(MockServerClient mockServer) {
    int pageSize = 2;

    fhirConsentProvider =
        new FhirConsentedPatientsProvider(
            httpClientBuilder.baseUrl(address).build(), policyHandler, meterRegistry);

    var operationOutcome = new OperationOutcome();
    var issue = operationOutcome.addIssue().setSeverity(IssueSeverity.ERROR);
    issue.setDiagnostics("No consents found for domain");

    var postRequest =
        request().withMethod("POST").withPath("/$allConsentsForPerson").withBody(jsonBody1);
    var httpResponse =
        response()
            .withStatusCode(404)
            .withBody(FhirUtils.fhirResourceToString(operationOutcome), APPLICATION_JSON);
    mockServer.when(postRequest).respond(httpResponse);

    create(
            fhirConsentProvider.fetch(
                consentRequest,
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
            httpClientBuilder.baseUrl(address).build(), policyHandler, meterRegistry);

    var operationOutcome = new OperationOutcome();
    var issue = operationOutcome.addIssue().setSeverity(IssueSeverity.ERROR);
    issue.setDiagnostics("Something's not right");

    var postRequest =
        request().withMethod("POST").withPath("/$allConsentsForPerson").withBody(jsonBody1);
    var httpResponse =
        response()
            .withStatusCode(404)
            .withBody(FhirUtils.fhirResourceToString(operationOutcome), APPLICATION_JSON);
    mockServer.when(postRequest).respond(httpResponse);

    create(
            fhirConsentProvider.fetch(
                consentRequest,
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
            httpClientBuilder.baseUrl(address).build(), policyHandler, meterRegistry);

    var operationOutcome = new OperationOutcome();
    operationOutcome.addIssue().setSeverity(IssueSeverity.ERROR);

    var postRequest =
        request().withMethod("POST").withPath("/$allConsentsForPerson").withBody(jsonBody1);
    var httpResponse =
        response()
            .withStatusCode(404)
            .withBody(FhirUtils.fhirResourceToString(operationOutcome), APPLICATION_JSON);
    mockServer.when(postRequest).respond(httpResponse);

    create(
            fhirConsentProvider.fetch(
                consentRequest,
                fromUriString("http://trustcenteragent:1234"),
                new PagingParams(0, pageSize)))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void emptyPoliciesYieldEmptyBundle(MockServerClient mockServer) {
    int totalEntries = 0;
    int pageSize = 2;

    var policyHandler = new PolicyHandler(Set.of());
    var consentRequest =
        new ConsentRequest("MII", Set.of(), POLICY_SYSTEM, List.of("id1", "id2", "id3", "id4"));
    fhirConsentProvider =
        new FhirConsentedPatientsProvider(
            httpClientBuilder.baseUrl(address).build(), policyHandler, meterRegistry);
    Bundle bundle =
        Stream.generate(gicsConsentGenerator::generateString)
            .limit(totalEntries)
            .map(FhirUtils::stringToFhirBundle)
            .collect(toBundle())
            .setTotal(totalEntries);

    HttpRequest postRequest =
        request().withMethod("POST").withPath("/$allConsentsForPerson").withBody(jsonBody1);
    HttpResponse httpResponse =
        response().withBody(FhirUtils.fhirResourceToString(bundle), APPLICATION_JSON);
    mockServer.when(postRequest).respond(httpResponse);

    create(
            fhirConsentProvider.fetch(
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
