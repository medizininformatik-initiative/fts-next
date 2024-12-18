package care.smith.fts.cda.rest.it.mock;

import static care.smith.fts.test.FhirGenerators.randomUuid;
import static care.smith.fts.test.FhirGenerators.resolveSearchResponse;
import static care.smith.fts.test.MockServerUtil.connectionReset;
import static care.smith.fts.test.MockServerUtil.delayedResponse;
import static care.smith.fts.test.MockServerUtil.fhirResponse;
import static care.smith.fts.test.MockServerUtil.sequentialMock;
import static care.smith.fts.util.FhirUtils.fhirResourceToString;
import static care.smith.fts.util.FhirUtils.toBundle;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.status;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.TEXT_PLAIN_VALUE;

import care.smith.fts.test.FhirGenerator;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.io.IOException;
import java.util.List;
import lombok.Builder;
import org.hl7.fhir.r4.model.Bundle;

@Builder
public class MockFhirResolveService {

  private final WireMock hds;
  private final MappingBuilder mockRequestSpec;

  public MockFhirResolveService(WireMock hds, MappingBuilder mockRequestSpec) {
    this.hds = hds;
    this.mockRequestSpec = mockRequestSpec;
  }

  public void resolveId(String patientId) throws IOException {
    resolveId(patientId, List.of(200));
  }

  public void resolveId(String patientId, List<Integer> statusCodes) throws IOException {
    var fhirResolveGen = resolveSearchResponse(() -> patientId, randomUuid());
    var seq = sequentialMock(hds);
    var butLast = statusCodes.subList(0, statusCodes.size() - 1);
    for (var statusCode : butLast) {
      var response =
          statusCode < 400 ? successResponse(statusCode, fhirResolveGen) : status(statusCode);
      seq.then(mockRequestSpec, response);
    }
    seq.thereafter(mockRequestSpec, successResponse(statusCodes.getLast(), fhirResolveGen));
  }

  private static ResponseDefinitionBuilder successResponse(
      int statusCode, FhirGenerator<Bundle> fhirResolveGen) {
    return fhirResponse(fhirResolveGen.generateResource(), statusCode);
  }

  public void isDown() {
    hds.register(mockRequestSpec.willReturn(connectionReset()));
  }

  public void timeout() {
    hds.register(mockRequestSpec.willReturn(delayedResponse()));
  }

  public void wrongContentType() {
    hds.register(
        mockRequestSpec.willReturn(
            ok().withHeader(CONTENT_TYPE, TEXT_PLAIN_VALUE)
                .withBody(fhirResourceToString(new Bundle()))));
  }

  public void moreThanOneResult() throws IOException {
    var fhirResolveGen = resolveSearchResponse(() -> "id1", randomUuid());

    var bundle = fhirResolveGen.generateResources().limit(2).collect(toBundle());
    hds.register(mockRequestSpec.willReturn(fhirResponse(bundle, 200)));
  }

  public void emptyBundle() {
    hds.register(mockRequestSpec.willReturn(fhirResponse(new Bundle(), 200)));
  }
}
