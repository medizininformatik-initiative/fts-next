package care.smith.fts.cda.test;

import static care.smith.fts.util.auth.HTTPClientAuthMethod.AuthMethod.NONE;

import care.smith.fts.util.HTTPClientConfig;
import java.io.InputStream;
import org.mockserver.client.MockServerClient;

/**
 * This class is mainly used for loading test resources from the underlying package (specifically
 * from other test packages) and may therefor be empty.
 */
public class MockServerUtil {
  public static HTTPClientConfig clientConfig(MockServerClient server) {
    var address = "http://localhost:%d".formatted(server.getPort());
    return new HTTPClientConfig(address, NONE);
  }

  public static InputStream getResourceAsStream(String resourceName) {
    return MockServerUtil.class.getResourceAsStream(resourceName);
  }
}
