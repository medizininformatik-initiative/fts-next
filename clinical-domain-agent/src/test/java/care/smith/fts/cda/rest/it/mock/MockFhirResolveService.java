package care.smith.fts.cda.rest.it.mock;

import static care.smith.fts.test.FhirGenerators.randomUuid;
import static care.smith.fts.util.FhirUtils.fhirResourceToString;
import static care.smith.fts.util.FhirUtils.toBundle;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;
import static org.mockserver.model.HttpResponse.response;

import care.smith.fts.test.FhirGenerator;
import care.smith.fts.test.FhirGenerators;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import org.hl7.fhir.r4.model.Bundle;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpError;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.MediaType;

@Builder
public class MockFhirResolveService {

  private final MockServerClient hds;
  private final HttpRequest mockRequestSpec;

  public MockFhirResolveService(MockServerClient hds, HttpRequest mockRequestSpec) {
    this.hds = hds;
    this.mockRequestSpec = mockRequestSpec;
  }

  public void resolveId(String patientId) throws IOException {
    resolveId(patientId, List.of());
  }

  public void resolveId(String patientId, List<Integer> statusCodes) throws IOException {
    var fhirResolveGen = FhirGenerators.resolveSearchResponse(() -> patientId, randomUuid());
    var rs = new LinkedList<>(statusCodes);
    hds.when(mockRequestSpec)
        .respond(
            request ->
                Optional.ofNullable(rs.poll())
                    .map(
                        statusCode ->
                            statusCode < 400
                                ? successResponse(statusCode, fhirResolveGen)
                                : response().withStatusCode(statusCode))
                    .orElseGet(() -> successResponse(200, fhirResolveGen)));
  }

  private static HttpResponse successResponse(
      Integer statusCode, FhirGenerator<Bundle> fhirResolveGen) {
    return response()
        .withStatusCode(statusCode)
        .withContentType(MediaType.parse(APPLICATION_FHIR_JSON_VALUE))
        .withBody(fhirResourceToString(fhirResolveGen.generateResource()));
  }

  public void isDown() {
    hds.when(mockRequestSpec).error(HttpError.error().withDropConnection(true));
  }

  public void timeout() {
    hds.when(mockRequestSpec).respond(request -> null, Delay.minutes(10));
  }

  public void wrongContentType() {
    hds.when(mockRequestSpec)
        .respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.PLAIN_TEXT_UTF_8)
                .withBody(fhirResourceToString(new Bundle())));
  }

  public void moreThanOneResult() throws IOException {
    var fhirResolveGen = FhirGenerators.resolveSearchResponse(() -> "id1", randomUuid());

    hds.when(mockRequestSpec)
        .respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.parse(APPLICATION_FHIR_JSON_VALUE))
                .withBody(
                    fhirResourceToString(
                        fhirResolveGen.generateResources().limit(2).collect(toBundle()))));
  }

  public void emptyBundle() {
    hds.when(mockRequestSpec)
        .respond(
            response()
                .withStatusCode(200)
                .withContentType(MediaType.parse(APPLICATION_FHIR_JSON_VALUE))
                .withBody(fhirResourceToString(new Bundle())));
  }
}
