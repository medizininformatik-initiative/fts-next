package care.smith.fts.cda.rest.it.mock;

import static java.util.UUID.randomUUID;

import care.smith.fts.test.FhirGenerator;
import care.smith.fts.test.FhirGenerator.Incrementing;
import care.smith.fts.util.FhirUtils;
import care.smith.fts.util.MediaTypes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.function.Supplier;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public class MockCohortSelector {

  private final MockServerClient tca;

  public MockCohortSelector(MockServerClient tca) {
    this.tca = tca;
  }

  private FhirGenerator validConsent(Supplier<String> patientId) throws IOException {
    return FhirGenerator.gicsResponse(() -> randomUUID().toString(), patientId);
  }

  public void successOnePatient(String patientId) throws IOException {
    successNPatients(patientId, 1);
  }

  public void successNPatients(String idPrefix, int n) throws IOException {
    var consent = validConsent(Incrementing.withPrefix(idPrefix)).generateBundle(n, n);
    tca.when(HttpRequest.request().withMethod("POST").withPath("/api/v2/cd/consented-patients"))
        .respond(
            HttpResponse.response()
                .withStatusCode(200)
                .withContentType(MediaType.parse(MediaTypes.APPLICATION_FHIR_JSON_VALUE))
                .withBody(FhirUtils.fhirResourceToString(consent)));
  }

  public void isDown() {
    tca.when(HttpRequest.request()).error(HttpError.error().withDropConnection(true));
  }

  public void timeout() {
    tca.when(HttpRequest.request().withMethod("POST").withPath("/api/v2/cd/consented-patients"))
        .respond(request -> null, Delay.minutes(10));
  }

  public void wrongContentType() throws IOException {
    var consent = validConsent(() -> "id1").generateBundle(1, 1);
    tca.when(HttpRequest.request().withMethod("POST").withPath("/api/v2/cd/consented-patients"))
        .respond(
            HttpResponse.response()
                .withStatusCode(200)
                .withContentType(MediaType.PLAIN_TEXT_UTF_8)
                .withBody(FhirUtils.fhirResourceToString(consent)));
  }

  public void unknownDomain(ObjectMapper om) throws JsonProcessingException {
    tca.when(HttpRequest.request().withMethod("POST").withPath("/api/v2/cd/consented-patients"))
        .respond(
            HttpResponse.response()
                .withStatusCode(400)
                .withContentType(MediaType.APPLICATION_JSON)
                .withBody(
                    om.writeValueAsString(
                        ProblemDetail.forStatusAndDetail(
                            HttpStatus.BAD_REQUEST, "No consents found for domain  'MII1234'"))));
  }
}
