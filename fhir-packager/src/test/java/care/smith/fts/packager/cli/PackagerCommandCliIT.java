package care.smith.fts.packager.cli;

import care.smith.fts.packager.config.MockPseudonymizerTestConfiguration;
import care.smith.fts.packager.service.BundleProcessor;
import care.smith.fts.packager.service.StdinReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for PackagerCommand with Spring Boot context.
 * 
 * <p>These tests verify the full CLI integration including:
 * <ul>
 *   <li>Spring Boot application startup and dependency injection</li>
 *   <li>Configuration loading from application properties</li>
 *   <li>CLI argument processing and validation</li>
 *   <li>Help text generation</li>
 *   <li>Error handling and exit codes</li>
 * </ul>
 */
@SpringBootTest(classes = MockPseudonymizerTestConfiguration.class)
@ActiveProfiles("test")
class PackagerCommandCliIT {

  @MockBean
  private BundleProcessor bundleProcessor;
  
  @MockBean
  private StdinReader stdinReader;
  
  @Autowired
  private PackagerCommand packageCommand;
  
  @BeforeEach
  void setUp() {
    // Reset the mocks before each test to ensure clean state
    reset(bundleProcessor);
    reset(stdinReader);
  }

  @Test
  void shouldShowHelpText() {
    PackagerCommand command = new PackagerCommand();
    CommandLine commandLine = new CommandLine(command);
    StringWriter out = new StringWriter();
    commandLine.setOut(new PrintWriter(out));

    int exitCode = commandLine.execute("--help");

    assertThat(exitCode).isEqualTo(0);
    String helpText = out.toString();
    assertThat(helpText).contains("fhir-packager");
    assertThat(helpText).contains("Pseudonymizes FHIR Bundles");
    assertThat(helpText).contains("--pseudonymizer-url");
    assertThat(helpText).contains("--timeout");
    assertThat(helpText).contains("--retries");
    assertThat(helpText).contains("--verbose");
    assertThat(helpText).contains("--config-file");
  }

  @Test
  void shouldShowVersionInfo() {
    PackagerCommand command = new PackagerCommand();
    CommandLine commandLine = new CommandLine(command);
    StringWriter out = new StringWriter();
    commandLine.setOut(new PrintWriter(out));

    int exitCode = commandLine.execute("--version");

    assertThat(exitCode).isEqualTo(0);
    String versionText = out.toString();
    assertThat(versionText).contains("1.0.0");
  }

  @Test
  void shouldReturnInvalidArgsExitCodeForBadUrl() {
    PackagerCommand command = new PackagerCommand();
    CommandLine commandLine = new CommandLine(command);

    int exitCode = commandLine.execute("--pseudonymizer-url", "not-a-url");

    assertThat(exitCode).isEqualTo(2);
  }

  @Test
  void shouldReturnInvalidArgsExitCodeForNegativeTimeout() {
    PackagerCommand command = new PackagerCommand();
    CommandLine commandLine = new CommandLine(command);
    StringWriter err = new StringWriter();
    commandLine.setErr(new PrintWriter(err));

    int exitCode = commandLine.execute("--timeout", "-5");

    assertThat(exitCode).isEqualTo(2);
    String errorText = err.toString();
    assertThat(errorText).contains("Timeout must be at least 1 second");
  }

  @Test
  void shouldReturnInvalidArgsExitCodeForNegativeRetries() {
    PackagerCommand command = new PackagerCommand();
    CommandLine commandLine = new CommandLine(command);
    StringWriter err = new StringWriter();
    commandLine.setErr(new PrintWriter(err));

    int exitCode = commandLine.execute("--retries", "-2");

    assertThat(exitCode).isEqualTo(2);
    String errorText = err.toString();
    assertThat(errorText).contains("Retries must be at least 0");
  }

  @Test
  void shouldReturnInvalidArgsExitCodeForNonExistentConfigFile() {
    PackagerCommand command = new PackagerCommand();
    CommandLine commandLine = new CommandLine(command);

    int exitCode = commandLine.execute("--config-file", "/does/not/exist.yaml");

    assertThat(exitCode).isEqualTo(2);
  }

  @Test
  void shouldAcceptValidConfiguration() throws IOException {
    Path tempFile = Files.createTempFile("config", ".yaml");
    Files.writeString(tempFile, "pseudonymizer:\n  url: http://test.com");
    
    // Use a new PackagerCommand instance to avoid polluting the Spring bean
    PackagerCommand command = new PackagerCommand();
    
    try {
      CommandLine commandLine = new CommandLine(command);

      int exitCode = commandLine.execute(
          "--pseudonymizer-url", "https://valid.example.com",
          "--timeout", "45",
          "--retries", "2",
          "--verbose",
          "--config-file", tempFile.toString()
      );

      // This confirms that configuration validation passed
      assertThat(exitCode).isEqualTo(1);
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  @Test
  void shouldHandleMinimalValidArguments() throws Exception {
    // Since CLI defaults now match config defaults, no CLI overrides are applied
    // This uses the autowired (mocked) BundleProcessor and PseudonymizerClient
    // Mock successful input to demonstrate the CLI works end-to-end
    String testBundle = "{\"resourceType\":\"Bundle\",\"type\":\"collection\",\"entry\":[]}";
    when(stdinReader.readFromStdin()).thenReturn(testBundle);
    
    CommandLine commandLine = new CommandLine(packageCommand);

    int exitCode = commandLine.execute();

    assertThat(exitCode).isEqualTo(0); // Success with mocked pseudonymizer
  }

  @Test
  void shouldValidateHttpUrl() throws Exception {
    when(stdinReader.readFromStdin()).thenThrow(new IOException("No input available"));
    CommandLine commandLine = new CommandLine(packageCommand);

    int httpCode = commandLine.execute("--pseudonymizer-url", "http://example.com");
    assertThat(httpCode).isEqualTo(1); // General error due to stdin read failure, not validation error (2)
  }

  @Test
  void shouldValidateHttpsUrl() throws Exception {
    when(stdinReader.readFromStdin()).thenThrow(new IOException("No input available"));
    CommandLine commandLine = new CommandLine(packageCommand);

    int httpsCode = commandLine.execute("--pseudonymizer-url", "https://example.com");
    assertThat(httpsCode).isEqualTo(1); // General error due to stdin read failure, not validation error (2)
  }

  @Test
  void shouldRejectFtpUrl() {
    PackagerCommand command = new PackagerCommand();
    CommandLine commandLine = new CommandLine(command);

    int ftpCode = commandLine.execute("--pseudonymizer-url", "ftp://example.com");
    assertThat(ftpCode).isEqualTo(2);
  }

  @Test
  void shouldValidateUrlComponents() throws Exception {
    when(stdinReader.readFromStdin()).thenThrow(new IOException("No input available"));
    CommandLine commandLine = new CommandLine(packageCommand);

    int exitCode = commandLine.execute("--pseudonymizer-url", "https://example.com:9090/api/v1");
    assertThat(exitCode).isEqualTo(1); // General error due to stdin read failure, not validation error (2)
  }

  @Test
  void shouldRejectMalformedUrls() {
    PackagerCommand command = new PackagerCommand();
    CommandLine commandLine = new CommandLine(command);

    // Test cases for malformed URLs
    String[] malformedUrls = {
        "://no-protocol.com",
        "http://",
        "not-a-url-at-all",
        "http:// space.com",
        "http://[invalid-bracket.com"
    };

    for (String url : malformedUrls) {
      // Reset command for each test
      command = new PackagerCommand();
      commandLine = new CommandLine(command);
      
      int exitCode = commandLine.execute("--pseudonymizer-url", url);
      
      assertThat(exitCode).as("URL should be rejected: " + url).isEqualTo(2);
    }
  }

  private String loadTestResource(String resourcePath) throws Exception {
    ClassPathResource resource = new ClassPathResource(resourcePath);
    return resource.getContentAsString(StandardCharsets.UTF_8);
  }
}