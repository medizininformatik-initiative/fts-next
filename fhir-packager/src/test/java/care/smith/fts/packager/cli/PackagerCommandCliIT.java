package care.smith.fts.packager.cli;

import care.smith.fts.packager.config.MockPseudonymizerTestConfiguration;
import care.smith.fts.packager.service.BundleProcessor;
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
import java.nio.file.Files;
import java.nio.file.Path;

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
  
  @Autowired
  private PackagerCommand packageCommand;
  
  @BeforeEach
  void setUp() {
    // Reset the mock before each test to ensure clean state
    reset(bundleProcessor);
  }

  @Test
  void shouldShowHelpText() {
    // Given: PackagerCommand with help argument
    PackagerCommand command = new PackagerCommand();
    CommandLine commandLine = new CommandLine(command);
    StringWriter out = new StringWriter();
    commandLine.setOut(new PrintWriter(out));

    // When: Execute help command
    int exitCode = commandLine.execute("--help");

    // Then: Help text should be displayed and exit code should be 0
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
    // Given: PackagerCommand with version argument
    PackagerCommand command = new PackagerCommand();
    CommandLine commandLine = new CommandLine(command);
    StringWriter out = new StringWriter();
    commandLine.setOut(new PrintWriter(out));

    // When: Execute version command
    int exitCode = commandLine.execute("--version");

    // Then: Version should be displayed and exit code should be 0
    assertThat(exitCode).isEqualTo(0);
    String versionText = out.toString();
    assertThat(versionText).contains("1.0.0");
  }

  @Test
  void shouldReturnInvalidArgsExitCodeForBadUrl() {
    // Given: PackagerCommand with invalid URL
    PackagerCommand command = new PackagerCommand();
    CommandLine commandLine = new CommandLine(command);

    // When: Execute with invalid URL
    int exitCode = commandLine.execute("--pseudonymizer-url", "not-a-url");

    // Then: Should return invalid arguments exit code
    assertThat(exitCode).isEqualTo(2);
  }

  @Test
  void shouldReturnInvalidArgsExitCodeForNegativeTimeout() {
    // Given: PackagerCommand with negative timeout
    PackagerCommand command = new PackagerCommand();
    CommandLine commandLine = new CommandLine(command);
    StringWriter err = new StringWriter();
    commandLine.setErr(new PrintWriter(err));

    // When: Execute with negative timeout
    int exitCode = commandLine.execute("--timeout", "-5");

    // Then: Should return invalid arguments exit code with error message
    assertThat(exitCode).isEqualTo(2);
    String errorText = err.toString();
    assertThat(errorText).contains("Timeout must be at least 1 second");
  }

  @Test
  void shouldReturnInvalidArgsExitCodeForNegativeRetries() {
    // Given: PackagerCommand with negative retries
    PackagerCommand command = new PackagerCommand();
    CommandLine commandLine = new CommandLine(command);
    StringWriter err = new StringWriter();
    commandLine.setErr(new PrintWriter(err));

    // When: Execute with negative retries
    int exitCode = commandLine.execute("--retries", "-2");

    // Then: Should return invalid arguments exit code with error message
    assertThat(exitCode).isEqualTo(2);
    String errorText = err.toString();
    assertThat(errorText).contains("Retries must be at least 0");
  }

  @Test
  void shouldReturnInvalidArgsExitCodeForNonExistentConfigFile() {
    // Given: PackagerCommand with non-existent config file
    PackagerCommand command = new PackagerCommand();
    CommandLine commandLine = new CommandLine(command);

    // When: Execute with non-existent config file
    int exitCode = commandLine.execute("--config-file", "/does/not/exist.yaml");

    // Then: Should return invalid arguments exit code
    assertThat(exitCode).isEqualTo(2);
  }

  @Test
  void shouldAcceptValidConfiguration() throws IOException {
    // Given: Valid config file and arguments + mocked bundle processor
    Path tempFile = Files.createTempFile("config", ".yaml");
    Files.writeString(tempFile, "pseudonymizer:\n  url: http://test.com");
    when(bundleProcessor.processBundle()).thenReturn(0);
    
    try {
      CommandLine commandLine = new CommandLine(packageCommand);

      // When: Execute with valid configuration
      int exitCode = commandLine.execute(
          "--pseudonymizer-url", "https://valid.example.com",
          "--timeout", "45",
          "--retries", "2",
          "--verbose",
          "--config-file", tempFile.toString()
      );

      // Then: Should succeed with valid configuration
      assertThat(exitCode).isEqualTo(0);
      verify(bundleProcessor).processBundle();
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  @Test
  void shouldHandleMinimalValidArguments() {
    // Given: Spring-injected PackagerCommand + mocked bundle processor
    when(bundleProcessor.processBundle()).thenReturn(0);
    
    CommandLine commandLine = new CommandLine(packageCommand);

    // When: Execute with just URL
    int exitCode = commandLine.execute("--pseudonymizer-url", "http://localhost:8080");

    // Then: Should succeed with defaults
    assertThat(exitCode).isEqualTo(0);
    verify(bundleProcessor).processBundle();
  }

  @Test
  void shouldValidateHttpUrl() {
    // Given: PackagerCommand + mocked bundle processor
    when(bundleProcessor.processBundle()).thenReturn(0);
    
    CommandLine commandLine = new CommandLine(packageCommand);

    // When & Then: HTTP should be valid
    int httpCode = commandLine.execute("--pseudonymizer-url", "http://example.com");
    assertThat(httpCode).isEqualTo(0);
  }

  @Test
  void shouldValidateHttpsUrl() {
    // Given: PackagerCommand + mocked bundle processor
    when(bundleProcessor.processBundle()).thenReturn(0);
    
    CommandLine commandLine = new CommandLine(packageCommand);

    // When & Then: HTTPS should be valid
    int httpsCode = commandLine.execute("--pseudonymizer-url", "https://example.com");
    assertThat(httpsCode).isEqualTo(0);
  }

  @Test
  void shouldRejectFtpUrl() {
    // Given: PackagerCommand
    CommandLine commandLine = new CommandLine(packageCommand);

    // When & Then: FTP should be invalid (fails during URL validation)
    int ftpCode = commandLine.execute("--pseudonymizer-url", "ftp://example.com");
    assertThat(ftpCode).isEqualTo(2);
  }

  @Test
  void shouldValidateUrlComponents() {
    // Given: PackagerCommand + mocked bundle processor
    when(bundleProcessor.processBundle()).thenReturn(0);
    
    CommandLine commandLine = new CommandLine(packageCommand);

    // When & Then: URL with port should be valid
    int exitCode = commandLine.execute("--pseudonymizer-url", "https://example.com:9090/api/v1");
    assertThat(exitCode).isEqualTo(0);
    verify(bundleProcessor).processBundle();
  }

  @Test
  void shouldRejectMalformedUrls() {
    // Given: PackagerCommand with malformed URLs
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
      
      // When: Execute with malformed URL
      int exitCode = commandLine.execute("--pseudonymizer-url", url);
      
      // Then: Should return invalid arguments exit code
      assertThat(exitCode).as("URL should be rejected: " + url).isEqualTo(2);
    }
  }
}