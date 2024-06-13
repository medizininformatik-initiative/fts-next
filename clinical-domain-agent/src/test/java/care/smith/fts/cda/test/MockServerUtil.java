package care.smith.fts.cda.test;

import static care.smith.fts.util.auth.HTTPClientAuthMethod.AuthMethod.NONE;

import care.smith.fts.util.HTTPClientConfig;
import org.mockserver.client.MockServerClient;

public class MockServerUtil {
  public static HTTPClientConfig clientConfig(MockServerClient server) {
    var address = "http://localhost:%d".formatted(server.getPort());
    return new HTTPClientConfig(address, NONE);
  }
}
