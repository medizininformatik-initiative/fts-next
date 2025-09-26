package care.smith.fts.cda;

import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

import com.github.tomakehurst.wiremock.client.WireMock;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class FHIRCohortSelectorE2E extends AbstractCohortSelectorE2E {

  public FHIRCohortSelectorE2E() {
    super("fhir-consent-example.yaml");
  }

  @Override
  protected void setupSpecificCdHdsMocks() throws IOException {
    var cdHdsWireMock = new WireMock(cdHds.getHost(), cdHds.getPort());
    var cohortGenerator = createCohortGenerator();
    var patient = cohortGenerator.generate();

    cdHdsWireMock.register(
        get(urlPathMatching("/fhir/Consent"))
            .withQueryParam("_include", equalTo("Consent:patient"))
            .willReturn(fhirResponse(patient)));

    cdHdsWireMock.register(
        get(urlPathMatching("/fhir/Consent"))
            .withQueryParam("_include", equalTo("Consent:patient"))
            .withQueryParam(
                "patient.identifier", equalTo("http://fts.smith.care|patient-identifier-1"))
            .willReturn(fhirResponse(patient)));
  }

  @Test
  void testStartTransferAllProcessWithExampleProject() {
    var testBodyValues = List.of("[]", "[\"patient-identifier-1\"]");
    for (String bodyValue : testBodyValues) {
      log.info("Testing with body value: {}", bodyValue);
      executeTransferTest(bodyValue);
    }
  }
}
