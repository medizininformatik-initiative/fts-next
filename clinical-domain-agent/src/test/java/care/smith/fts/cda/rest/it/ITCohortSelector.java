package care.smith.fts.cda.rest.it;

import care.smith.fts.test.FhirGenerator;
import care.smith.fts.test.FhirGenerator.Fixed;
import care.smith.fts.test.FhirGenerator.UUID;
import care.smith.fts.util.FhirUtils;
import care.smith.fts.util.MediaTypes;
import java.io.IOException;
import org.hl7.fhir.r4.model.Bundle;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;

public class ITCohortSelector {

  private final MockServerClient tca;

  public ITCohortSelector(MockServerClient tca) {
    this.tca = tca;
  }

  private Bundle validConsent(String patientId) throws IOException {
    FhirGenerator gicsConsentGenerator = new FhirGenerator("GicsResponseTemplate.json");
    gicsConsentGenerator.replaceTemplateFieldWith("$QUESTIONNAIRE_RESPONSE_ID", new UUID());
    gicsConsentGenerator.replaceTemplateFieldWith("$PATIENT_ID", new Fixed(patientId));
    return gicsConsentGenerator.generateBundle(1, 1);
  }

  void success(String patientId) throws IOException {
    var consent = validConsent(patientId);
    tca.when(HttpRequest.request().withMethod("POST").withPath("/api/v2/cd/consented-patients"))
        .respond(
            HttpResponse.response()
                .withStatusCode(200)
                //                .withContentType(MediaType.APPLICATION_JSON)
                .withContentType(MediaType.parse(MediaTypes.APPLICATION_FHIR_JSON_VALUE))
                .withBody(FhirUtils.fhirResourceToString(consent)));
  }

  void isDown() {
    tca.when(HttpRequest.request()).error(HttpError.error().withDropConnection(true));
  }

  void timeoutResponse() {
    tca.when(HttpRequest.request().withMethod("POST").withPath("/api/v2/cd/consented-patients"))
        .respond(request -> null, Delay.minutes(10));
  }

  void wrongContentType() throws IOException {
    var consent = validConsent("id1");
    tca.when(HttpRequest.request().withMethod("POST").withPath("/api/v2/cd/consented-patients"))
        .respond(
            HttpResponse.response()
                .withStatusCode(200)
                .withContentType(MediaType.PLAIN_TEXT_UTF_8)
                .withBody(FhirUtils.fhirResourceToString(consent)));
  }
}
