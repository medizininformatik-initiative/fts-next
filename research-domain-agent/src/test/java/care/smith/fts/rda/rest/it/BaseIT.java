package care.smith.fts.rda.rest.it;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.util.FileSystemUtils.deleteRecursively;

import care.smith.fts.test.MockServerUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.mockserver.client.MockServerClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class BaseIT {
  private static final Path tempDir;

  protected static final MockServerClient hds;
  protected static final MockServerClient tca;

  @AfterAll
  static void afterAll() throws IOException {
    deleteRecursively(tempDir);
  }

  @DynamicPropertySource
  static void registerTempDir(DynamicPropertyRegistry registry) {
    registry.add("projects.directory", tempDir::toString);
  }

  protected static void resetAll() {
    tca.reset();
    hds.reset();
  }

  static {
    try {
      tempDir = Files.createTempDirectory("ftsit");
      hds = hdsMockServer();
      tca = tcaMockServer();
      createProject();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create project config file", e);
    }
  }

  private static void createProject() throws IOException {
    var projectFile = Files.createFile(tempDir.resolve("test.yaml"));
    try (var inStream =
            TransferProcessControllerIT.class.getResourceAsStream("project-template.yaml");
        var outStream = Files.newOutputStream(projectFile)) {
      var config =
          new String(inStream.readAllBytes(), UTF_8)
              .replace("<tc-agent>", "http://localhost:%d".formatted(tca.getPort()))
              .replace("<hds>", "http://localhost:%d".formatted(hds.getPort()));
      outStream.write(config.getBytes(UTF_8));
    }
  }

  private static MockServerClient tcaMockServer() throws IOException {
    return MockServerUtil.onRandomPort();
  }

  private static MockServerClient hdsMockServer() throws IOException {
    return MockServerUtil.onRandomPort();
  }
}
