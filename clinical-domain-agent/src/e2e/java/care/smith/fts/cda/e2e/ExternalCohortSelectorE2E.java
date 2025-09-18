package care.smith.fts.cda.e2e;

import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static care.smith.fts.test.MockServerUtil.jsonResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;

import care.smith.fts.test.FhirCohortGenerator;
import care.smith.fts.test.FhirGenerators;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;

public class ExternalCohortSelectorE2E extends AbstractCohortSelectorE2E {

  @Override
  protected String getProjectFileName() {
    return "external-consent-example.yaml";
  }

  @Override
  protected List<String> getTestBodyValues() {
    return List.of("[\"patient-identifier-1\"]");
  }

  @Override
  protected void configureCdHdsMocks() throws IOException {
    var cdHdsWireMock = new WireMock(cdHds.getHost(), cdHds.getPort());

    var resolveResponse =
        FhirGenerators.resolveSearchResponse(
                () -> "patient-1", () -> "patient-identifier-1", () -> "resolveId")
            .generateResource();

    cdHdsWireMock.register(
        get(urlPathMatching("/fhir/Patient"))
            .withQueryParam("identifier", equalTo("http://fts.smith.care|patient-identifier-1"))
            .willReturn(fhirResponse(resolveResponse)));

    var cohortGenerator =
        new FhirCohortGenerator(
            "http://fts.smith.care",
            "urn:oid:2.16.840.1.113883.3.1937.777.24.5.3",
            Set.of(
                "2.16.840.1.113883.3.1937.777.24.5.3.3",
                "2.16.840.1.113883.3.1937.777.24.5.3.2",
                "2.16.840.1.113883.3.1937.777.24.5.3.7",
                "2.16.840.1.113883.3.1937.777.24.5.3.6"));

    var patient =
        new Bundle().addEntry(new BundleEntryComponent().setResource(cohortGenerator.generate()));

    cdHdsWireMock.register(
        get(urlPathMatching("/fhir/Patient/patient-1.*")).willReturn(fhirResponse(patient)));

    cdHdsWireMock.register(
        get(urlEqualTo("/metadata"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader(CONTENT_TYPE, "application/fhir+json")
                    .withBody(
                        """
                        {
                          "resourceType": "CapabilityStatement",
                          "status": "active",
                          "fhirVersion": "4.0.1"
                        }
                        """)));
  }

  @Override
  protected void configureTcaMocks() {
    var tcaWireMock = new WireMock(tca.getHost(), tca.getPort());

    tcaWireMock.register(
        post(urlPathMatching("/api/v2/cd/transport-mapping.*"))
            .willReturn(
                jsonResponse(
                    """
                    {
                      "transferId": "transfer-123",
                      "transportMapping": {
                        "patient-identifier-1.Patient:patient-1": "pseudonym-123",
                        "patient-identifier-1.identifier.http://fts.smith.care:patient-identifier-1": "pseudonym-identifier-123"
                      },
                      "dateShiftValue": 1209600.000000000
                    }
                    """)));
  }

}
