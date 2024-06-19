package care.smith.fts.tca.consent;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockserver.client.MockServerClient;
import org.mockserver.junit.jupiter.MockServerExtension;
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

  @BeforeEach
  void setUp(MockServerClient mockServer) {
    var address = "http://localhost:%d".formatted(mockServer.getPort());

    fhirConsentProvider =
        new FhirConsentProvider(
            httpClientBuilder.baseUrl(address).build(), policyHandler, defaultPageSize);
  }

  @Test
  void paging(MockServerClient mockServer) throws IOException {
    int totalEntries = 2 * defaultPageSize;

    FhirGenerator gicsConsentGenerator = new FhirGenerator("GicsResponseTemplate.json");
    gicsConsentGenerator.replaceTemplateFieldWith("$QUESTIONNAIRE_RESPONSE_ID", new UUID());
    gicsConsentGenerator.replaceTemplateFieldWith("$PATIENT_ID", new UUID());

    Bundle bundle = gicsConsentGenerator.generateBundle(totalEntries, defaultPageSize);
    mockServer
        .when(
            request()
                .withMethod("POST")
                .withPath("/$allConsentsForDomain")
                .withQueryStringParameters(
                    List.of(new Parameter("_offset", "0"), new Parameter("_count", "200")))
                .withBody(
                    json(
                        """
                                {
                                 "resourceType": "Parameters",
                                 "parameter": [{"name": "domain", "valueString": "MII"}]
                                }
                                """,
                        ONLY_MATCHING_FIELDS)))
        .respond(response().withBody(FhirUtils.fhirResourceToString(bundle), APPLICATION_JSON));

    create(fhirConsentProvider.consentedPatientsPage("MII", POLICY_SYSTEM, POLICIES, "123"))
        .assertNext(b -> b.equalsDeep(bundle))
        .verifyComplete();
  }

  @Test
  void httpClientThrowsIOException() throws IOException {
    //    given(httpClient.execute(any(HttpPost.class), any(HttpClientResponseHandler.class)))
    //        .willThrow(new IOException());
    //    assertThatExceptionOfType(IOException.class)
    //        .isThrownBy(() -> fhirConsentProvider.allConsentedPatients("any", defaultPolicies));
  }
}
