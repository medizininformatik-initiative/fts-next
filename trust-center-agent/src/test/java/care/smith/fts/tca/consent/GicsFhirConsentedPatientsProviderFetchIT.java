package care.smith.fts.tca.consent;

import static care.smith.fts.test.FhirGenerators.fromList;
import static care.smith.fts.test.FhirGenerators.randomUuid;
import static care.smith.fts.test.MockServerUtil.APPLICATION_FHIR_JSON;
import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static care.smith.fts.util.FhirUtils.fhirResourceToString;
import static care.smith.fts.util.FhirUtils.toBundle;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.common.ContentTypes.CONTENT_TYPE;
import static com.github.tomakehurst.wiremock.matching.UrlPattern.ANY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.I_AM_A_TEAPOT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static org.springframework.web.util.UriComponentsBuilder.fromUriString;
import static reactor.test.StepVerifier.create;

import care.smith.fts.tca.consent.ConsentedPatientsProvider.PagingParams;
import care.smith.fts.test.FhirGenerator;
import care.smith.fts.test.FhirGenerators;
import care.smith.fts.test.TestWebClientFactory;
import care.smith.fts.util.FhirUtils;
import care.smith.fts.util.error.fhir.FhirException;
import care.smith.fts.util.error.fhir.NoFhirServerException;
import care.smith.fts.util.tca.ConsentFetchRequest;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
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
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@SpringBootTest
@WireMockTest
@Import(TestWebClientFactory.class)
class GicsFhirConsentedPatientsProviderFetchIT {

  @Autowired WebClient.Builder httpClientBuilder;
  @Autowired MeterRegistry meterRegistry;

  @MockitoBean
  RedissonClient redisClient; // We need to mock the redisClient otherwise the tests won't start

  private static final String POLICY_SYSTEM =
      "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy";
  private static final String PATIENT_IDENTIFIER_SYSTEM =
      "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym";
  private GicsFhirConsentedPatientsProvider fhirConsentProvider;

  private static final Set<String> POLICIES =
      Set.of(
          "IDAT_erheben",
          "IDAT_speichern_verarbeiten",
          "MDAT_erheben",
          "MDAT_speichern_verarbeiten");
  private static final ConsentFetchRequest CONSENT_FETCH_REQUEST =
      new ConsentFetchRequest(
          "MII",
          POLICIES,
          POLICY_SYSTEM,
          PATIENT_IDENTIFIER_SYSTEM,
          List.of("id1", "id2", "id3", "id4"));

  private static final String requestBody1 =
      """
            {
              "resourceType": "Parameters",
              "parameter": [
                {"name": "domain", "valueString": "MII"},
                {"name":  "personIdentifier", "valueIdentifier":  {"system":  "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym", "value": "id1"}},
                {"name":  "personIdentifier", "valueIdentifier":  {"system":  "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym", "value": "id2"}}
            ]}
            """;

  private static final String requestBody2 =
      """
      {
        "resourceType": "Parameters",
        "parameter": [
          {"name": "domain", "valueString": "MII"},
          {"name":  "personIdentifier", "valueIdentifier":  {"system":  "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym", "value": "id3"}},
          {"name":  "personIdentifier", "valueIdentifier":  {"system":  "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym", "value": "id4"}}
      ]}
      """;

  private WireMock wireMock;
  private FhirGenerator<Bundle> gicsConsentGenerator;
  private static final MappingBuilder gicsRequest =
      post(urlPathEqualTo("/$allConsentsForPerson")).withRequestBody(equalToJson(requestBody1));

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntime) throws IOException {
    var address = wireMockRuntime.getHttpBaseUrl();
    wireMock = wireMockRuntime.getWireMock();
    gicsConsentGenerator =
        FhirGenerators.gicsResponse(randomUuid(), fromList(List.of("id1", "id2", "id3", "id4")));
    fhirConsentProvider =
        new GicsFhirConsentedPatientsProvider(
            httpClientBuilder.baseUrl(address).build(), meterRegistry);
  }

  @AfterEach
  void tearDown() {
    wireMock.resetMappings();
  }

  private static CapabilityStatement gicsMockCapabilityStatement() {
    var capabilities = new CapabilityStatement();
    var rest = capabilities.addRest();
    rest.addOperation().setName("allConsentsForDomain");
    rest.addOperation().setName("allConsentsForPerson");
    return capabilities;
  }

  @Test
  void paging() {
    int pageSize = 2;
    int totalEntries = 2 * pageSize;

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
            .withRequestBody(equalToJson(requestBody1))
            .willReturn(fhirResponse(bundle1)));

    wireMock.register(
        post("/$allConsentsForPerson")
            .withRequestBody(equalToJson(requestBody2))
            .willReturn(fhirResponse(bundle2)));

    var expectedNextLink =
        "http://trustcenteragent:8080/api/v2/cd/consented-patients/fetch?from=%s&count=%s"
            .formatted(pageSize, pageSize);

    log.info("Get first page");
    create(
            fhirConsentProvider.fetch(
                CONSENT_FETCH_REQUEST,
                fromUriString("http://trustcenteragent:8080"),
                new PagingParams(0, pageSize)))
        .assertNext(
            consentBundle ->
                assertThat(consentBundle.getLink("next").getUrl()).isEqualTo(expectedNextLink))
        .verifyComplete();
    log.info("Get second page");
    create(
            fhirConsentProvider.fetch(
                CONSENT_FETCH_REQUEST,
                fromUriString("http://trustcenteragent:8080"),
                new PagingParams(pageSize, pageSize)))
        .assertNext(consentBundle -> assertThat(consentBundle.getLink()).isEmpty())
        .verifyComplete();
  }

  @Test
  void noNextLinkOnLastPage() {
    int totalEntries = 2;
    int pageSize = 2;

    var bundle =
        Stream.generate(gicsConsentGenerator::generateString)
            .limit(totalEntries)
            .map(FhirUtils::stringToFhirBundle)
            .collect(toBundle())
            .setTotal(totalEntries);

    wireMock.register(
        post(urlPathEqualTo("/$allConsentsForPerson"))
            .withRequestBody(equalToJson(requestBody2))
            .willReturn(fhirResponse(bundle)));

    create(
            fhirConsentProvider.fetch(
                CONSENT_FETCH_REQUEST,
                fromUriString("http://trustcenteragent:8080"),
                new PagingParams(2, pageSize)))
        .assertNext(consentBundle -> assertThat(consentBundle.getLink("next")).isNull())
        .verifyComplete();
  }

  @Test
  void noConsents() {
    int totalEntries = 0;
    int pageSize = 2;

    var bundle =
        Stream.generate(gicsConsentGenerator::generateString)
            .limit(totalEntries)
            .map(FhirUtils::stringToFhirBundle)
            .collect(toBundle())
            .setTotal(totalEntries);

    wireMock.register(gicsRequest.willReturn(fhirResponse(bundle)));

    var expectedNextLink =
        "http://trustcenteragent:8080/api/v2/cd/consented-patients/fetch?from=%s&count=%s"
            .formatted(pageSize, pageSize);
    create(
            fhirConsentProvider.fetch(
                CONSENT_FETCH_REQUEST,
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
  void emptyPoliciesYieldEmptyBundle() {
    var consentRequest =
        new ConsentFetchRequest(
            "MII",
            Set.of(),
            POLICY_SYSTEM,
            PATIENT_IDENTIFIER_SYSTEM,
            List.of("id1", "id2", "id3", "id4"));

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
    var consentRequest =
        new ConsentFetchRequest(
            "MII", POLICIES, POLICY_SYSTEM, PATIENT_IDENTIFIER_SYSTEM, List.of());
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
  void serverIsNoGics() {
    wireMock.register(
        gicsRequest.willReturn(
            status(I_AM_A_TEAPOT.value()).withBody("Would you like some milk?")));
    wireMock.register(
        get(urlPathEqualTo("/metadata"))
            .withQueryParam("_elements", equalTo("rest.operation"))
            .willReturn(status(I_AM_A_TEAPOT.value()).withBody("Would you like some milk?")));
    create(
            fhirConsentProvider.fetch(
                CONSENT_FETCH_REQUEST,
                fromUriString("http://trustcenteragent:8080"),
                new PagingParams(0, 2)))
        .expectError(NoFhirServerException.class)
        .verify();
  }

  private void gics4xxResponse(
      String body, String message, HttpStatus gicsResponseStatus, HttpStatus returnStatus) {
    wireMock.register(
        gicsRequest.willReturn(
            status(gicsResponseStatus.value())
                .withHeader(CONTENT_TYPE, APPLICATION_FHIR_JSON)
                .withBody(body)));
    wireMock.register(
        get(urlPathEqualTo("/metadata"))
            .withQueryParam("_elements", equalTo("rest.operation"))
            .willReturn(
                status(OK.value())
                    .withHeader(CONTENT_TYPE, APPLICATION_FHIR_JSON)
                    .withBody(fhirResourceToString(gicsMockCapabilityStatement()))));
    create(
            fhirConsentProvider.fetch(
                CONSENT_FETCH_REQUEST,
                fromUriString("http://trustcenteragent:8080"),
                new PagingParams(0, 2)))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(FhirException.class).hasMessage(message);
              assertThat(((FhirException) error).getStatusCode()).isEqualTo(returnStatus);
            })
        .verify();
  }

  @Test
  void serverIsGicsButDoesNotSendOperationOutcome() {
    gics4xxResponse(
        fhirResourceToString(new Bundle()),
        "Unexpected Error: Cannot parse OperationOutcome from gICS",
        BAD_REQUEST,
        INTERNAL_SERVER_ERROR);
  }

  @Test
  void gicsReturnsBadRequest() {
    gics4xxResponse(
        fhirResourceToString(new OperationOutcome()),
        "Fehlende oder fehlerhafte Parameter.",
        BAD_REQUEST,
        BAD_REQUEST);
  }

  @Test
  void gicsReturnsUnauthorized() {
    gics4xxResponse(
        fhirResourceToString(new OperationOutcome()),
        "Invalid gICS FHIR gateway configuration",
        UNAUTHORIZED,
        INTERNAL_SERVER_ERROR);
  }

  @Test
  void gicsReturnsNotFound() {
    gics4xxResponse(
        fhirResourceToString(new OperationOutcome()),
        "Parameter mit unbekanntem Inhalt",
        NOT_FOUND,
        NOT_FOUND);
  }

  @Test
  void gicsReturnsUnprocessableEntity() {
    gics4xxResponse(
        fhirResourceToString(new OperationOutcome()),
        "Fehlende oder falsche Patienten-Attribute.",
        UNPROCESSABLE_ENTITY,
        UNPROCESSABLE_ENTITY);
  }

  @Test
  void diagnosticsBadRequestInHandle4xxError() {
    var operationOutcome = new OperationOutcome();
    var issue = operationOutcome.addIssue();
    issue.setSeverity(IssueSeverity.ERROR);
    issue.setDiagnostics("Something's not right");

    wireMock.register(gicsRequest.willReturn(fhirResponse(operationOutcome, BAD_REQUEST)));

    create(
            fhirConsentProvider.fetch(
                CONSENT_FETCH_REQUEST,
                fromUriString("http://trustcenteragent:1234"),
                new PagingParams(0, 2)))
        .expectError(FhirException.class)
        .verify();
  }

  @Test
  void responseHasNoOperationOutcome() {
    wireMock.register(
        gicsRequest.willReturn(
            status(UNAUTHORIZED.value())
                .withHeader(CONTENT_TYPE, "text/plain")
                .withBody("Unauthorized")));
    wireMock.register(
        get("/metadata")
            .willReturn(
                status(UNAUTHORIZED.value())
                    .withHeader(CONTENT_TYPE, "text/plain")
                    .withBody("Unauthorized")));
    create(
            fhirConsentProvider.fetch(
                CONSENT_FETCH_REQUEST,
                fromUriString("http://trustcenteragent:8080"),
                new PagingParams(0, 2)))
        .expectError(NoFhirServerException.class)
        .verify();
  }

  @Test
  void gicsReturns500() {
    wireMock.register(
        post(ANY)
            .willReturn(
                status(INTERNAL_SERVER_ERROR.value())
                    .withBody("what was I supposed to do again?")));
    create(
            fhirConsentProvider.fetch(
                CONSENT_FETCH_REQUEST,
                fromUriString("http://trustcenteragent:8080"),
                new PagingParams(0, 2)))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(FhirException.class).hasMessage("gICS kapuut");
              assertThat(((FhirException) error).getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
            })
        .verify();
  }

  @Test
  void noGicsServer() {
    fhirConsentProvider =
        new GicsFhirConsentedPatientsProvider(
            httpClientBuilder.baseUrl("http://does-not-exist").build(), meterRegistry);
    create(
            fhirConsentProvider.fetch(
                CONSENT_FETCH_REQUEST,
                fromUriString("http://trustcenteragent:8080"),
                new PagingParams(0, 2)))
        .expectError(NoFhirServerException.class)
        .verify();
  }
}
