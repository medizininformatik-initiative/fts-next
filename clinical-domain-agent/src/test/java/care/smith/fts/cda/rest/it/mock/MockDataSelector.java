package care.smith.fts.cda.rest.it.mock;

import org.mockserver.client.MockServerClient;

public class MockDataSelector {

  MockFhirResolveService mockFhirResolveService;
  MockTransportIds mockTransportIds;
  MockFetchData mockFetchData;

  public MockFhirResolveService getMockFhirResolveService() {
    return mockFhirResolveService;
  }

  public MockTransportIds getMockTransportIds() {
    return mockTransportIds;
  }

  public MockFetchData getMockFetchData() {
    return mockFetchData;
  }

  public MockDataSelector(MockServerClient tca, MockServerClient hds) {
    this.mockFhirResolveService = new MockFhirResolveService(hds);
    this.mockFetchData = new MockFetchData(hds);
    this.mockTransportIds = new MockTransportIds(tca);
  }
}
