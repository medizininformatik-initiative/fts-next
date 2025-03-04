package care.smith.fts.tca.consent;

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
import static java.lang.String.valueOf;
import static java.util.Map.entry;
import static java.util.Map.ofEntries;
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
import care.smith.fts.util.tca.ConsentFetchAllRequest;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@SpringBootTest
@WireMockTest
@Import(TestWebClientFactory.class)
class GicsFhirConsentedPatientsProviderFetchAllIT {

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
  private FhirGenerator<Bundle> gicsConsentGenerator;
  private static final String jsonBody =
      """
  {
   "resourceType": "Parameters",
   "parameter": [{"name": "domain", "valueString": "MII"}]
  }
  """;
  private static WireMock wireMock;
  private GicsFhirConsentedPatientsProvider fhirConsentProvider;
  private static final MappingBuilder gicsRequest =
      post(urlPathEqualTo("/$allConsentsForDomain")).withRequestBody(equalToJson(jsonBody));

  @BeforeEach
  void setUp(WireMockRuntimeInfo wireMockRuntime) throws IOException {
    var address = wireMockRuntime.getHttpBaseUrl();
    wireMock = wireMockRuntime.getWireMock();
    gicsConsentGenerator = FhirGenerators.gicsResponse(randomUuid(), randomUuid());
    fhirConsentProvider =
        new GicsFhirConsentedPatientsProvider(
            httpClientBuilder.baseUrl(address).build(), meterRegistry);
  }

  @AfterEach
  void tearDown() {
    wireMock.resetMappings();
  }

  private static @NotNull CapabilityStatement gicsMockCapabilityStatement() {
    var capabilities = new CapabilityStatement();
    var rest = capabilities.addRest();
    rest.addResource().setProfile("gics");
    rest.addOperation().setName("allConsentsForDomain");
    rest.addOperation().setName("allConsentsForPerson");
    return capabilities;
  }

  @Test
  void paging() {
    int totalEntries = 2 * defaultPageSize;

    Bundle bundle =
        gicsConsentGenerator
            .generateResources()
            .limit(defaultPageSize)
            .collect(toBundle())
            .setTotal(totalEntries);

    wireMock.register(
        gicsRequest
            .withQueryParams(
                ofEntries(
                    entry("_offset", equalTo("0")),
                    entry("_count", equalTo(valueOf(defaultPageSize)))))
            .willReturn(fhirResponse(bundle)));
    wireMock.register(
        post(urlPathEqualTo("/$allConsentsForDomain"))
            .withRequestBody(equalToJson(jsonBody))
            .withQueryParams(
                ofEntries(
                    entry("_offset", equalTo(valueOf(defaultPageSize))),
                    entry("_count", equalTo(valueOf(defaultPageSize)))))
            .willReturn(fhirResponse(bundle)));

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
  void noNextLinkOnLastPage() {
    int totalEntries = 1;
    int pageSize = 1;

    var bundle =
        Stream.generate(gicsConsentGenerator::generateString)
            .limit(totalEntries)
            .map(FhirUtils::stringToFhirBundle)
            .collect(toBundle())
            .setTotal(totalEntries);

    wireMock.register(
        gicsRequest
            .withQueryParams(
                ofEntries(
                    entry("_offset", equalTo("0")), entry("_count", equalTo(valueOf(pageSize)))))
            .willReturn(fhirResponse(bundle)));

    create(
            fhirConsentProvider.fetchAll(
                CONSENT_FETCH_ALL_REQUEST,
                fromUriString("http://trustcenteragent:1234"),
                new PagingParams(0, pageSize)))
        .assertNext(consentBundle -> assertThat(consentBundle.getLink("next")).isNull())
        .verifyComplete();
  }

  @Test
  void noConsents() {
    int totalEntries = 0;
    int pageSize = 1;
    var bundle =
        Stream.generate(gicsConsentGenerator::generateString)
            .limit(totalEntries)
            .map(FhirUtils::stringToFhirBundle)
            .collect(toBundle())
            .setTotal(totalEntries);

    wireMock.register(
        gicsRequest
            .withQueryParams(
                ofEntries(
                    entry("_offset", equalTo("0")), entry("_count", equalTo(valueOf(pageSize)))))
            .willReturn(fhirResponse(bundle)));

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
  void emptyPoliciesYieldEmptyBundle() {
    int totalEntries = 0;
    int pageSize = 2;
    var consentRequest = new ConsentFetchAllRequest("MII", Set.of(), POLICY_SYSTEM);
    var bundle =
        Stream.generate(gicsConsentGenerator::generateString)
            .limit(totalEntries)
            .map(FhirUtils::stringToFhirBundle)
            .collect(toBundle())
            .setTotal(totalEntries);

    wireMock.register(
        gicsRequest
            .withQueryParams(
                ofEntries(
                    entry("_offset", equalTo("0")), entry("_count", equalTo(valueOf(pageSize)))))
            .willReturn(fhirResponse(bundle)));

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

  @Test
  void serverIsNoGics() {
    wireMock.register(
        gicsRequest.willReturn(
            status(I_AM_A_TEAPOT.value()).withBody("Would you like some milk?")));
    wireMock.register(
        get(urlPathEqualTo("/metadata"))
            .willReturn(status(I_AM_A_TEAPOT.value()).withBody("Would you like some milk?")));
    create(
            fhirConsentProvider.fetchAll(
                CONSENT_FETCH_ALL_REQUEST,
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
            .willReturn(
                status(OK.value())
                    .withHeader(CONTENT_TYPE, APPLICATION_FHIR_JSON)
                    .withBody(FhirUtils.fhirResourceToString(gicsMockCapabilityStatement()))));
    create(
            fhirConsentProvider.fetchAll(
                CONSENT_FETCH_ALL_REQUEST,
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
        "Invalid GICS FHIR gateway configuration",
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
            fhirConsentProvider.fetchAll(
                CONSENT_FETCH_ALL_REQUEST,
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
            fhirConsentProvider.fetchAll(
                CONSENT_FETCH_ALL_REQUEST,
                fromUriString("http://trustcenteragent:8080"),
                new PagingParams(0, 2)))
        .expectError(FhirException.class)
        .verify();
  }

  @Test
  void noGicsServer() {
    fhirConsentProvider =
        new GicsFhirConsentedPatientsProvider(
            httpClientBuilder.baseUrl("http://does-not-exist").build(), meterRegistry);
    create(
            fhirConsentProvider.fetchAll(
                CONSENT_FETCH_ALL_REQUEST,
                fromUriString("http://trustcenteragent:8080"),
                new PagingParams(0, 2)))
        .expectError(NoFhirServerException.class)
        .verify();
  }

  @Test
  void gicsReturns5xx() {
    wireMock.register(gicsRequest.willReturn(status(INTERNAL_SERVER_ERROR.value())));
    create(
            fhirConsentProvider.fetchAll(
                CONSENT_FETCH_ALL_REQUEST,
                fromUriString("http://trustcenteragent:8080"),
                new PagingParams(0, 2)))
        .expectErrorSatisfies(
            error -> {
              assertThat(error).isInstanceOf(FhirException.class).hasMessage("");
              assertThat(((FhirException) error).getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
            })
        .verify();
  }
}
