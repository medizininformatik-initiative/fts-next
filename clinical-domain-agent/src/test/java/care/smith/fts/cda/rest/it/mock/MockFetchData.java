package care.smith.fts.cda.rest.it.mock;

import static care.smith.fts.test.MockServerUtil.connectionReset;
import static care.smith.fts.test.MockServerUtil.delayedResponse;
import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static care.smith.fts.test.MockServerUtil.sequentialMock;
import static care.smith.fts.util.FhirUtils.fhirResourceToString;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static com.github.tomakehurst.wiremock.matching.UrlPattern.ANY;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.util.List;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;

@Slf4j
@Builder
public class MockFetchData {

  private final WireMock hds;
  private final MappingBuilder mockRequestSpec;

  public MockFetchData(WireMock hds, MappingBuilder mockRequestSpec) {
    this.hds = hds;
    this.mockRequestSpec = mockRequestSpec;
  }

  public void respondWith(Bundle patient) {
    respondWith(patient, List.of(200));
  }

  public void respondWith(Bundle patient, List<Integer> statusCodes) {
    var seq = sequentialMock(hds);
    var butLast = statusCodes.subList(0, statusCodes.size() - 1);
    for (var statusCode : butLast) {
      var response = statusCode < 400 ? successResponse(statusCode, patient) : status(statusCode);
      seq = seq.then(mockRequestSpec, response);
    }
    seq.thereafter(mockRequestSpec, successResponse(statusCodes.getLast(), patient));
  }

  private static ResponseDefinitionBuilder successResponse(int statusCode, Bundle patient) {
    return fhirResponse(patient, statusCode);
  }

  public void dropConnection() {
    hds.register(any(ANY).willReturn(connectionReset()));
  }

  public void timeout() {
    hds.register(any(ANY).willReturn(delayedResponse()));
  }

  public void respondWithWrongContentType() {
    hds.register(
        mockRequestSpec.willReturn(
            ok().withHeader(CONTENT_TYPE, TEXT_PLAIN_VALUE)
                .withBody(fhirResourceToString(new Bundle()))));
  }

  public void respondWithEmptyBundle() {
    hds.register(mockRequestSpec.willReturn(notFound()));
  }
}
