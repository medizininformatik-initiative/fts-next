package care.smith.fts.cda;

import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import com.github.tomakehurst.wiremock.client.WireMock;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.junit.jupiter.api.Test;

@Slf4j
public class TCACohortSelectorE2E extends AbstractCohortSelectorE2E {

  public TCACohortSelectorE2E() {
    super("gics-consent-example.yaml");
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
  }

  @Test
  void testStartTransferAllProcessWithExampleProject() {
    var testBodyValues = List.of("[]");
    for (String bodyValue : testBodyValues) {
      log.info("Testing with body value: {}", bodyValue);
      executeTransferTest(bodyValue);
    }
  }
}
