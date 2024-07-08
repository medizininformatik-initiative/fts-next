package care.smith.fts.cda.rest.it;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import care.smith.fts.test.FhirGenerator;
import care.smith.fts.test.FhirGenerator.Fixed;
import care.smith.fts.test.FhirGenerator.UUID;
import care.smith.fts.util.FhirUtils;
import java.io.IOException;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.MediaType;

class ITFhirResolveService {

  private final MockServerClient hds;

  public ITFhirResolveService(MockServerClient hds) {
    this.hds = hds;
  }

  void success(String patientId, String identifierSystem) throws IOException {
    var fhirResolveGen = new FhirGenerator("FhirResolveSearchRequestTemplate.json");
    fhirResolveGen.replaceTemplateFieldWith("$PATIENT_ID", new Fixed(patientId));
    fhirResolveGen.replaceTemplateFieldWith("$HDS_ID", new UUID());

    hds.when(
            request()
                .withMethod("GET")
                .withHeader("accept", APPLICATION_FHIR_JSON_VALUE)
                .withPath("/Patient")
                .withQueryStringParameter("identifier", identifierSystem + "|" + patientId))
        .respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.parse(APPLICATION_FHIR_JSON_VALUE))
                .withBody(
                    FhirUtils.fhirResourceToString(
                        fhirResolveGen.generateBundle(1, 1).getEntryFirstRep().getResource())));
  }
}
