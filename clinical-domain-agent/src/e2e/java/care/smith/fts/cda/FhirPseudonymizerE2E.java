package care.smith.fts.cda;

import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static care.smith.fts.test.MockServerUtil.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.github.tomakehurst.wiremock.client.WireMock;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.utility.MountableFile;
import org.wiremock.integrations.testcontainers.WireMockContainer;

@Slf4j
public class FhirPseudonymizerE2E extends AbstractCohortSelectorE2E {

  private final WireMockContainer fp =
      new WireMockContainer("wiremock/wiremock:3.13.0")
          .withCreateContainerCmdModifier(cmd -> cmd.withName("cda-e2e-fp-fp-example"))
          .withNetwork(network)
          .withNetworkAliases("fhir-pseudonymizer");

  public FhirPseudonymizerE2E() {
    super("fp-example.yaml");
    cda.withCopyFileToContainer(
        MountableFile.forClasspathResource("fp-example/anonymization-config.yaml"),
        "/app/projects/fp-example/anonymization-config.yaml");
  }

  @BeforeEach
  void setUpFp() {
    fp.start();
    configureFpMocks();
  }

  @AfterEach
  void tearDownFp() {
    if (fp.isRunning()) {
      fp.stop();
    }
  }

  private void configureFpMocks() {
    var fpWireMock = new WireMock(fp.getHost(), fp.getPort());

    // FP returns a bundle with 32-char Base64URL resource IDs (transport IDs)
    var patient = new Patient();
    patient.setId("AbCdEfGhIjKlMnOpQrStUvWxYz012345");
    var responseBundle = new Bundle();
    responseBundle.setType(Bundle.BundleType.COLLECTION);
    responseBundle.addEntry().setResource(patient);

    fpWireMock.register(
        post(urlPathEqualTo("/fhir/$de-identify")).willReturn(fhirResponse(responseBundle)));
  }

  @Override
  protected void setupSpecificTcaMocks() {
    var tcaWireMock = new WireMock(tca.getHost(), tca.getPort());

    var cohortGenerator =
        createCohortGenerator("https://ths-greifswald.de/fhir/gics/identifiers/Pseudonym");
    var tcaResponse =
        new Bundle()
            .setEntry(List.of(new BundleEntryComponent().setResource(cohortGenerator.generate())));

    tcaWireMock.register(
        post(urlPathMatching("/api/v2/cd/consented-patients.*"))
            .withHeader(CONTENT_TYPE, equalTo(APPLICATION_JSON_VALUE))
            .willReturn(fhirResponse(tcaResponse)));

    tcaWireMock.register(
        post(urlPathEqualTo("/api/v2/cd/fhir-pseudonymizer/transport-mapping"))
            .willReturn(
                jsonResponse(
                    """
                    {"transferId": "AbCdEfGhIjKlMnOpQrStUvWxYz012345"}
                    """)));
  }

  @Test
  void testFhirPseudonymizerTransfer() {
    executeTransferTest("[]");
  }
}
