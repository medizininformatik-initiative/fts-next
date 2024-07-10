package care.smith.fts.cda.rest.it.mock;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;
import static org.mockserver.model.HttpRequest.request;

import org.mockserver.client.MockServerClient;

public class MockDataSelector {

  private final MockFhirResolveService mockFhirResolveService;
  private final MockTransportIds mockTransportIds;

  private final MockServerClient hds;

  public MockDataSelector(MockServerClient tca, MockServerClient hds) {
    this.mockFhirResolveService = new MockFhirResolveService(hds);
    this.mockTransportIds = new MockTransportIds(tca);
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

  public MockFhirResolveService getMockFhirResolveService() {
    return this.mockFhirResolveService;
  }

  public MockTransportIds getMockTransportIds() {
    return this.mockTransportIds;
  }
}
