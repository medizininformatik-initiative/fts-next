package care.smith.fts.cda.rest.it;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.util.FileSystemUtils.deleteRecursively;

import care.smith.fts.test.MockServerUtil;
import com.github.tomakehurst.wiremock.WireMockServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import no.nav.security.mock.oauth2.MockOAuth2Server;
import org.junit.jupiter.api.AfterAll;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class BaseIT {
  private static final Path tempDir;

  protected static final WireMockServer hds;
  protected static final WireMockServer tca;
  protected static final WireMockServer rda;
  protected static final MockOAuth2Server oauth2Server;

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
      oauth2Server = new MockOAuth2Server();
      oauth2Server.start();
      createProject();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to create project config file", e);
    }
  }

  //
  //  @DynamicPropertySource
  //  static void registerOauth2MockUrl(DynamicPropertyRegistry registry) {
  //    System.out.println(oauth2Server.baseUrl());
  //    System.out.println(oauth2Server.issuerUrl(""));
  //    System.out.println(oauth2Server.userInfoUrl(""));
  //
  //    System.out.println(oauth2Server.wellKnownUrl(""));
  //
  //    var url = oauth2Server.wellKnownUrl("");
  //    registry.add("spring.security.oauth2.client.provider.keycloak.issuer-uri", url::toString);
  //    registry.add("security.auth.oauth2.issuer", url::toString);
  //
  //    registry.add(
  //        "spring.security.oauth2.client.registration.agent.authorizationGrantType",
  //        () -> "client_credentials");
  //    registry.add("spring.security.oauth2.client.registration.agent.clientId", () ->
  // "fts/cd-agent");
  //    registry.add(
  //        "spring.security.oauth2.client.registration.agent.clientSecret",
  //        () -> "eA4xj1zFxsVYZGdLah9KnkcmHYDBjojr");
  //    registry.add("spring.security.oauth2.client.registration.agent.provider", () -> "keycloak");
  //  }

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
