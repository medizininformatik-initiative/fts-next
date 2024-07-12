package care.smith.fts.rda.rest.it.mock;

import org.mockserver.client.MockServerClient;

public class MockBundleSender {
  private final MockServerClient hds;

  public MockBundleSender(MockServerClient hds) {
    this.hds = hds;
  }
}
