package care.smith.fts.cda.rest.it;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import care.smith.fts.util.FhirUtils;
import org.hl7.fhir.r4.model.Bundle;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.MediaType;

class ITFetchData {

  private final MockServerClient hds;

  public ITFetchData(MockServerClient hds) {
    this.hds = hds;
  }

  void success(String patientId, Bundle patient) {
    hds.when(
            request()
                .withMethod("GET")
                .withHeader("accept", APPLICATION_FHIR_JSON_VALUE)
                .withPath("/Patient/%s/$everything".formatted(patientId))
                .withQueryStringParameter("start", "2023-07-29")
                .withQueryStringParameter("end", "2028-07-29"))
        .respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.parse(APPLICATION_FHIR_JSON_VALUE))
                .withBody(FhirUtils.fhirResourceToString(patient)));
  }
}
