package care.smith.fts.cda.e2e;

import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static care.smith.fts.test.MockServerUtil.jsonResponse;
import static care.smith.fts.util.fhir.FhirUtils.fhirResourceToString;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

import care.smith.fts.test.FhirCohortGenerator;
import care.smith.fts.test.FhirGenerators;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;

public class FHIRCohortSelectorE2E extends AbstractCohortSelectorE2E {

  @Override
  protected String getProjectFileName() {
    return "fhir-consent-example.yaml";
  }

  @Override
  protected List<String> getTestBodyValues() {
    return List.of("[]", "[\"patient-identifier-1\"]");
  }

  @Override
  protected void configureCdHdsMocks() throws IOException {
    var cdHdsWireMock = new WireMock(cdHds.getHost(), cdHds.getPort());

    // Enable request logging to see what URLs are being called
    System.out.println("=== WireMock CD-HDS Configuration ===");
    System.out.println("WireMock URL: http://" + cdHds.getHost() + ":" + cdHds.getPort());

    // Add a catch-all stub to log all requests that don't match other stubs
    cdHdsWireMock.register(
        any(anyUrl())
            .willReturn(
                aResponse().withStatus(404).withBody("{\"error\": \"No matching stub found\"}"))
            .atPriority(10) // Lower priority so specific stubs match first
        );

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

    var patient = cohortGenerator.generate();

    var patientBundle = new Bundle().addEntry(new BundleEntryComponent().setResource(patient));
    System.out.println("Patient bundle: " + fhirResourceToString(patient));

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

    cdHdsWireMock.register(
        get(urlPathMatching("/fhir/Patient/patient-1.*")).willReturn(fhirResponse(patientBundle)));
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
