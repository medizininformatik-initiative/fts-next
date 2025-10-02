package care.smith.fts.tca;

import static care.smith.fts.test.FhirGenerators.randomUuid;
import static care.smith.fts.test.FhirGenerators.withPrefix;
import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static care.smith.fts.util.fhir.FhirUtils.toBundle;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.test.FhirGenerators;
import care.smith.fts.util.fhir.FhirUtils;
import care.smith.fts.util.tca.ConsentFetchAllRequest;
import care.smith.fts.util.tca.ConsentFetchRequest;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

/**
 * E2E test for the consented patients endpoints which retrieve patient information based on consent
 * status from gICS.
 */
@Slf4j
public class ConsentEndpointsE2E extends AbstractTcaE2E {

  @Override
  protected void configureGpasMocks() throws IOException {
    configureGpasMetadataMock();
  }

  @Override
  protected void configureGicsMocks() throws IOException {
    configureGicsMetadataMock();

    var gicsWireMock = new WireMock(gics.getHost(), gics.getPort());

    // Mock gICS consent query response - nested Bundle structure as expected by TCA
    // Use incrementing suppliers to generate unique IDs for each patient
    var consentGenerator =
        FhirGenerators.gicsResponse(randomUuid(), withPrefix("FTS", 1), withPrefix("patient-", 1));

    // Create outer bundle containing inner bundles (one per patient)
    var outerBundle =
        Stream.generate(consentGenerator::generateString)
            .limit(2)
            .map(FhirUtils::stringToFhirBundle)
            .collect(toBundle())
            .setTotal(2);

    // Mock for fetchAll endpoint (uses $allConsentsForDomain)
    gicsWireMock.register(
        post(urlPathEqualTo("/ttp-fhir/fhir/gics/$allConsentsForDomain"))
            .willReturn(fhirResponse(outerBundle)));

    // Mock for fetch endpoint (uses $allConsentsForPerson)
    gicsWireMock.register(
        post(urlPathEqualTo("/ttp-fhir/fhir/gics/$allConsentsForPerson"))
            .willReturn(fhirResponse(outerBundle)));
  }

  @Test
  void testFetchAllConsentedPatients() {
    var request = createFetchAllRequest();
    log.info("Sending fetch-all request: {}", request);

    executePostRequest(
        "/api/v2/cd/consented-patients/fetch-all",
        request,
        null,
        bundle -> {
          verifyNestedBundleStructure(bundle);
          log.info("Successfully retrieved {} consented patient bundles", bundle.getEntry().size());
        });
  }

  @Test
  void testFetchAllConsentedPatientsWithPagination() {
    var request = createFetchAllRequest();
    log.info("Sending fetch-all request with pagination: {}", request);

    executePostRequest(
        "/api/v2/cd/consented-patients/fetch-all",
        request,
        uriBuilder -> uriBuilder.queryParam("from", 0).queryParam("count", 10).build(),
        bundle -> {
          log.info("Received paginated bundle with {} entries", bundle.getEntry().size());
          assertThat(bundle).isNotNull();
          assertThat(bundle.getEntry()).isNotEmpty();
          assertThat(bundle.getEntry().size()).isLessThanOrEqualTo(10);
          log.info(
              "Successfully retrieved paginated results: {} patients", bundle.getEntry().size());
        });
  }

  @Test
  void testFetchConsentedPatients() {
    var request =
        new ConsentFetchRequest(
            "MII",
            Set.of("IDAT_erheben", "IDAT_speichern_verarbeiten"),
            "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy",
            "https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym",
            List.of("patient-1", "patient-2"));

    log.info("Sending fetch request for specific patients: {}", request);

    executePostRequest(
        "/api/v2/cd/consented-patients/fetch",
        request,
        null,
        bundle -> {
          verifyNestedBundleStructure(bundle);
          log.info(
              "Successfully retrieved {} specific consented patients", bundle.getEntry().size());
        });
  }

  private ConsentFetchAllRequest createFetchAllRequest() {
    return new ConsentFetchAllRequest(
        "MII",
        Set.of("IDAT_erheben", "IDAT_speichern_verarbeiten"),
        "https://ths-greifswald.de/fhir/CodeSystem/gics/Policy");
  }
}
