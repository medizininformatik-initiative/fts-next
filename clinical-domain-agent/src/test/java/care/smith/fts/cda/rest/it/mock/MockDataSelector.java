package care.smith.fts.cda.rest.it.mock;

import lombok.Getter;
import org.mockserver.client.MockServerClient;

@Getter
public class MockDataSelector {

  MockFhirResolveService mockFhirResolveService;
  MockTransportIds mockTransportIds;
  MockFetchData mockFetchData;

  public MockDataSelector(MockServerClient tca, MockServerClient hds) {
    this.mockFhirResolveService = new MockFhirResolveService(hds);
    this.mockFetchData = new MockFetchData(hds);
    this.mockTransportIds = new MockTransportIds(tca);
  }
}
