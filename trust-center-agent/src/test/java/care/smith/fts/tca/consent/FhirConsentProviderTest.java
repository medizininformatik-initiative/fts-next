package care.smith.fts.tca.consent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.matchers.MatchType.ONLY_MATCHING_FIELDS;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.APPLICATION_JSON;
import static reactor.test.StepVerifier.create;

import care.smith.fts.test.FhirGenerator;
import care.smith.fts.test.FhirGenerator.UUID;
import care.smith.fts.util.FhirUtils;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
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

@Slf4j
@SpringBootTest
@ExtendWith(MockServerExtension.class)
@ExtendWith(MockitoExtension.class)
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
  private static FhirGenerator gicsConsentGenerator;
  private static JsonBody jsonBody;

  @BeforeAll
  static void setUp(MockServerClient mockServer) throws IOException {
    address = "http://localhost:%d".formatted(mockServer.getPort());
    gicsConsentGenerator = new FhirGenerator("GicsResponseTemplate.json");
    gicsConsentGenerator.replaceTemplateFieldWith("$QUESTIONNAIRE_RESPONSE_ID", new UUID());
    gicsConsentGenerator.replaceTemplateFieldWith("$PATIENT_ID", new UUID());

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

    Bundle bundle = gicsConsentGenerator.generateBundle(totalEntries, defaultPageSize);

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

    var expectedNextLink =
        "http://trustcenteragent:1234/cd/consented-patients?from=%s&count=%s"
            .formatted(defaultPageSize, defaultPageSize);

    log.info("Get first page");
    create(
            fhirConsentProvider.consentedPatientsPage(
                "MII", POLICY_SYSTEM, POLICIES, "http://trustcenteragent:1234"))
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
                "http://trustcenteragent:1234",
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

    Bundle bundle = gicsConsentGenerator.generateBundle(totalEntries, pageSize);

    ;
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
                "MII", POLICY_SYSTEM, POLICIES, "http://trustcenteragent:1234"))
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
    Bundle bundle = gicsConsentGenerator.generateBundle(totalEntries, pageSize);

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
                "MII", POLICY_SYSTEM, POLICIES, "http://trustcenteragent:1234"))
        .assertNext(
            consentBundle -> {
              assertThat(consentBundle.getEntry()).isEmpty();
              assertThat(consentBundle.getLink("next")).isNull();
            })
        .verifyComplete();
  }
}
