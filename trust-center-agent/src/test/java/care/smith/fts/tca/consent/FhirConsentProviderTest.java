package care.smith.fts.tca.consent;

import care.smith.fts.test.FhirGenerator;
import care.smith.fts.test.FhirGenerator.UUID;
import java.io.IOException;
import java.util.HashSet;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@SpringBootTest
@ExtendWith(MockitoExtension.class)
class FhirConsentProviderTest {
  @Mock WebClient httpClient;
  @Autowired HashSet<String> defaultPolicies;
  @Autowired PolicyHandler policyHandler;

  private FhirConsentProvider fhirConsentProvider;

  @Qualifier("defaultPageSize")
  @Autowired
  private int defaultPageSize;

  @BeforeEach
  void setUp() {
    fhirConsentProvider = new FhirConsentProvider(httpClient, policyHandler, defaultPageSize);
  }

  @Test
  void paging() throws IOException {
    int totalEntries = 2 * defaultPageSize;

    FhirGenerator gicsConsentGenerator = new FhirGenerator("GicsResponseTemplate.json");
    gicsConsentGenerator.replaceTemplateFieldWith("$QUESTIONNAIRE_RESPONSE_ID", new UUID());
    gicsConsentGenerator.replaceTemplateFieldWith("$PATIENT_ID", new UUID());

    //    given(httpClient.execute(any(HttpPost.class), any(HttpClientResponseHandler.class)))
    //        .willAnswer(ignored -> gicsConsentGenerator.generateBundle(totalEntries, pageSize));
    //
    //    List<ConsentedPatient> consentedPatients =
    //        fhirConsentProvider.allConsentedPatients("any", defaultPolicies);
    //    assertThat(consentedPatients).hasSize(totalEntries);
  }

  @Test
  void httpClientThrowsIOException() throws IOException {
    //    given(httpClient.execute(any(HttpPost.class), any(HttpClientResponseHandler.class)))
    //        .willThrow(new IOException());
    //    assertThatExceptionOfType(IOException.class)
    //        .isThrownBy(() -> fhirConsentProvider.allConsentedPatients("any", defaultPolicies));
  }
}
