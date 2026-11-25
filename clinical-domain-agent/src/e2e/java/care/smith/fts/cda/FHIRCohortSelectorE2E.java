package care.smith.fts.cda;

import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static care.smith.fts.util.fhir.FhirUtils.toBundle;
import static care.smith.fts.util.fhir.FhirUtils.typedResourceStream;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

import com.github.tomakehurst.wiremock.client.WireMock;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Consent;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;

@Slf4j
public class FHIRCohortSelectorE2E extends AbstractCohortSelectorE2E {

  public FHIRCohortSelectorE2E() {
    super("fhir-consent-example.yaml");
  }

  /**
   * The search for a list of patients is the same request as the patient identifier resolution, so
   * the common resolve stub answers it with patient-1. The consent request returns its consents.
   */
  @Override
  protected void setupSpecificCdHdsMocks() throws IOException {
    var cdHdsWireMock = new WireMock(cdHds.getHost(), cdHds.getPort());
    var cohortGenerator = createCohortGenerator();
    var page = cohortGenerator.generate();
    var patients = typedResourceStream(page, Patient.class).collect(toBundle());
    var consents = typedResourceStream(page, Consent.class).collect(toBundle());

    cdHdsWireMock.register(
        get(urlPathMatching("/fhir/Patient"))
            .withQueryParam("_has:Consent:patient:status", equalTo("active"))
            .willReturn(fhirResponse(patients)));

    cdHdsWireMock.register(
        get(urlPathMatching("/fhir/Consent"))
            .withQueryParam(
                "patient.identifier", equalTo("http://fts.smith.care|patient-identifier-1"))
            .withQueryParam("_include", equalTo("Consent:patient"))
            .willReturn(fhirResponse(consents)));
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
