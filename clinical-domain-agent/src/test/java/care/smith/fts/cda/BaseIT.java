package care.smith.fts.cda;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.util.FileSystemUtils.deleteRecursively;

import care.smith.fts.cda.rest.TransferProcessControllerIT;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class BaseIT {
  private static final Path tempDir;

  protected static final MockServerClient hds;
  protected static final MockServerClient tca;
  protected static final MockServerClient rda;

  @AfterAll
  static void afterAll() throws IOException {
    deleteRecursively(tempDir);
  }

  @DynamicPropertySource
  static void registerTempDir(DynamicPropertyRegistry registry) {
    registry.add("projects.directory", tempDir::toString);
  }

  static {
    try {
      tempDir = Files.createTempDirectory("ftsit");
      hds = hdsMockServer();
      tca = tcaMockServer();
      rda = rdaMockServer();
      createProject();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create project config file", e);
    }
  }

  private static void createProject() throws IOException {
    var projectFile = Files.createFile(tempDir.resolve("test.yaml"));
    try (var inStream =
            TransferProcessControllerIT.class.getResourceAsStream("project-template.yaml");
        var outStream = Files.newOutputStream(projectFile); ) {
      var config =
          new String(inStream.readAllBytes(), UTF_8)
              .replace("<tc-agent>", "http://localhost:%d".formatted(tca.getPort()))
              .replace("<hds>", "http://localhost:%d".formatted(hds.getPort()))
              .replace("<rd-agent>", "http://localhost:%d".formatted(rda.getPort()));
      outStream.write(config.getBytes(UTF_8));
    }
  }

  private static MockServerClient tcaMockServer() throws IOException {
    return ClientAndServer.startClientAndServer(findFreePort());
  }

  private static MockServerClient rdaMockServer() throws IOException {
    return ClientAndServer.startClientAndServer(findFreePort());
  }

  private static MockServerClient hdsMockServer() throws IOException {
    return ClientAndServer.startClientAndServer(findFreePort());
  }

  public static int findFreePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      socket.setReuseAddress(true);
      return socket.getLocalPort();
    }
  }
}
