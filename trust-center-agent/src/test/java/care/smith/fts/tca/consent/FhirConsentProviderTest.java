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

import care.smith.fts.test.FhirGenerator;
import care.smith.fts.test.FhirGenerators;
import care.smith.fts.util.FhirUtils;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@SpringBootTest
@ExtendWith(MockServerExtension.class)
class FhirConsentProviderTest {
  @Autowired WebClient.Builder httpClientBuilder;
  @Autowired PolicyHandler policyHandler;

  private static final String POLICY_SYSTEM =
      "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy";
  private FhirConsentProvider fhirConsentProvider;

  @Qualifier("defaultPageSize")
  @Autowired
  private int defaultPageSize;

  private static final Set<String> POLICIES =
      Set.of(
          "IDAT_erheben",
          "IDAT_speichern_verarbeiten",
          "MDAT_erheben",
          "MDAT_speichern_verarbeiten");
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
        new FhirConsentProvider(
            httpClientBuilder.baseUrl(address).build(), policyHandler, defaultPageSize);

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
                    new Parameter("_count", String.valueOf(2 * defaultPageSize)))))
        .respond(httpResponse);

    var expectedNextLink = "/fake?from=%s&count=%s".formatted(defaultPageSize, defaultPageSize);

    log.info("Get first page");
    create(
            fhirConsentProvider.consentedPatientsPage(
                "MII", POLICY_SYSTEM, POLICIES, fromUriString("/fake")))
        .assertNext(
            consentBundle ->
                assertThat(consentBundle.getLink("next").getUrl()).isEqualTo(expectedNextLink))
        .verifyComplete();
    log.info("Get second page");
    create(
            fhirConsentProvider.consentedPatientsPage(
                "MII",
                POLICY_SYSTEM,
                POLICIES,
                fromUriString("/fake?from=0&count=200"),
                defaultPageSize,
                defaultPageSize))
        .assertNext(consentBundle -> assertThat(consentBundle.getLink()).isEmpty())
        .verifyComplete();
  }

  @Test
  void noNextLinkOnLastPage(MockServerClient mockServer) {
    int totalEntries = 1;
    int pageSize = 1;

    fhirConsentProvider =
        new FhirConsentProvider(
            httpClientBuilder.baseUrl(address).build(), policyHandler, pageSize);

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
            fhirConsentProvider.consentedPatientsPage(
                "MII", POLICY_SYSTEM, POLICIES, fromUriString("http://trustcenteragent:1234")))
        .assertNext(
            consentBundle -> {
              assertThat(consentBundle.getLink("next")).isNull();
            })
        .verifyComplete();
  }

  @Test
  void noConsents(MockServerClient mockServer) {
    int totalEntries = 0;
    int pageSize = 1;

    fhirConsentProvider =
        new FhirConsentProvider(
            httpClientBuilder.baseUrl(address).build(), policyHandler, pageSize);
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
            fhirConsentProvider.consentedPatientsPage(
                "MII", POLICY_SYSTEM, POLICIES, fromUriString("http://trustcenteragent:1234")))
        .assertNext(
            consentBundle -> {
              assertThat(consentBundle.getEntry()).isEmpty();
              assertThat(consentBundle.getLink("next")).isNull();
            })
        .verifyComplete();
  }

  @Test
  void negativePagingArgumentsThrowException() {
    fhirConsentProvider =
        new FhirConsentProvider(httpClientBuilder.baseUrl(address).build(), policyHandler, 1);

    assertErrorWithInvalidPagingArgs(-1, -1);
    assertErrorWithInvalidPagingArgs(-1, 0);
    assertErrorWithInvalidPagingArgs(0, -1);
  }

  private void assertErrorWithInvalidPagingArgs(int from, int count) {
    create(
            fhirConsentProvider.consentedPatientsPage(
                "domain",
                "policySystem",
                Set.of("Policy A"),
                UriComponentsBuilder.newInstance(),
                from,
                count))
        .expectErrorMessage("from and count must be non-negative")
        .verify();
  }

  @Test
  void tooLargePagingArgumentsThrowException() {
    fhirConsentProvider =
        new FhirConsentProvider(httpClientBuilder.baseUrl(address).build(), policyHandler, 1);
    create(
            fhirConsentProvider.consentedPatientsPage(
                "domain",
                "policySystem",
                Set.of("Policy A"),
                UriComponentsBuilder.newInstance(),
                Integer.MAX_VALUE - 10,
                20))
        .expectErrorMessage("from + count must be smaller than 2147483647")
        .verify();
  }
}
