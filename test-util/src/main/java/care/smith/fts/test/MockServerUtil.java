package care.smith.fts.test;

import static care.smith.fts.util.auth.HTTPClientAuthMethod.AuthMethod.NONE;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.MediaType.create;

import care.smith.fts.util.HTTPClientConfig;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import org.mockserver.client.MockServerClient;
import org.mockserver.model.MediaType;

/**
 * This class is mainly used for loading test resources from the underlying package (specifically
 * from other test packages) and may therefor be empty.
 */
public interface MockServerUtil {
  static HTTPClientConfig clientConfig(MockServerClient server) {
    var address = "http://localhost:%d".formatted(server.getPort());
    return new HTTPClientConfig(address, NONE);
  }

  static InputStream getResourceAsStream(String resourceName) {
    return MockServerUtil.class.getResourceAsStream(resourceName);
  }

  static MockServerClient onRandomPort() {
    return startClientAndServer(findFreePort());
  }

  private static int findFreePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      socket.setReuseAddress(true);
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to find free port", e);
    }
  }

  public static MediaType APPLICATION_FHIR_JSON = create("application", "fhir+json");
}
