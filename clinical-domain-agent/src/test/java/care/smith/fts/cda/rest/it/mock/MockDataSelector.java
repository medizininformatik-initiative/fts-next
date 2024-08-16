package care.smith.fts.cda.rest.it.mock;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.MediaType.APPLICATION_JSON;

import care.smith.fts.util.tca.PseudonymizeRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Set;
import org.mockserver.client.MockServerClient;

public class MockDataSelector {

  private final ObjectMapper om;
  private final MockServerClient hds;
  private final MockServerClient tca;

  public MockDataSelector(ObjectMapper om, MockServerClient tca, MockServerClient hds) {
    this.om = om;
    this.tca = tca;
    this.hds = hds;
  }

  public MockFetchData whenFetchData(String patientId) {
    return MockFetchData.builder()
        .hds(hds)
        .mockRequestSpec(
            request()
                .withMethod("GET")
                .withHeader("accept", APPLICATION_FHIR_JSON_VALUE)
                .withPath("/Patient/%s/$everything".formatted(patientId))
                .withQueryStringParameter("start", "2023-07-29")
                .withQueryStringParameter("end", "2028-07-29"))
        .build();
  }

  public MockFhirResolveService whenResolvePatient(String patientId, String identifierSystem) {
    return MockFhirResolveService.builder()
        .hds(hds)
        .mockRequestSpec(
            request()
                .withMethod("GET")
                .withHeader("accept", APPLICATION_FHIR_JSON_VALUE)
                .withPath("/Patient")
                .withQueryStringParameter("identifier", identifierSystem + "|" + patientId))
        .build();
  }

  public MockTransportIds whenTransportIds(String patientId, String identifierSystem)
      throws JsonProcessingException {
    var id1 = patientId + ".identifier." + identifierSystem + ":" + patientId;
    var id2 = patientId + ".Patient:" + patientId;

    Set<String> ids = Set.of(id1, id2);
    var pseudonymizeRequest = new PseudonymizeRequest(id1, ids, "MII", Duration.ofDays(14));
    return MockTransportIds.builder()
        .tca(tca)
        .transportIds(ids)
        .om(om)
        .mockRequestSpec(
            request()
                .withMethod("POST")
                .withContentType(APPLICATION_JSON)
                .withPath("/api/v2/cd/transport-ids-and-date-shifting-values")
                .withBody(json(om.writeValueAsString(pseudonymizeRequest))))
        .build();
  }
}
