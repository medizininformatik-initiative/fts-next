package care.smith.fts.cda.rest.it;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.util.FileSystemUtils.deleteRecursively;

import care.smith.fts.test.MockServerUtil;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class BaseIT {
  private static final Path tempDir;

  protected static final WireMockServer hds;
  protected static final WireMockServer tca;
  protected static final WireMockServer rda;

  @AfterAll
  static void afterAll() throws IOException {
    deleteRecursively(tempDir);
  }

  @DynamicPropertySource
  static void registerTempDir(DynamicPropertyRegistry registry) {
    registry.add("projects.directory", tempDir::toString);
  }

  protected static void resetAll() {
    tca.resetAll();
    hds.resetAll();
    rda.resetAll();
  }

  static {
    try {
      tempDir = Files.createTempDirectory("ftsit");
      hds = MockServerUtil.onRandomPort();
      tca = MockServerUtil.onRandomPort();
      rda = MockServerUtil.onRandomPort();
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
              .replace("<tc-agent>", tca.baseUrl())
              .replace("<hds>", hds.baseUrl())
              .replace("<rd-agent>", rda.baseUrl());
      outStream.write(config.getBytes(UTF_8));
    }
  }
}
