package care.smith.fts.cda.rest.it;

import org.mockserver.client.MockServerClient;

class ITDataSelector {

  ITFhirResolveService itFhirResolveService;
  ITTransportIds itTransportIds;
  ITFetchData itFetchData;

  public ITDataSelector(MockServerClient tca, MockServerClient hds) {
    this.itFhirResolveService = new ITFhirResolveService(hds);
    this.itFetchData = new ITFetchData(hds);
    this.itTransportIds = new ITTransportIds(tca);
  }
}
