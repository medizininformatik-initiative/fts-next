package care.smith.fts.packager.cli;

import care.smith.fts.packager.config.PseudonymizerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PackagerCommand CLI argument parsing and validation.
 */
@ExtendWith(MockitoExtension.class)
class PackagerCommandTest {

  @Mock
  private PseudonymizerConfig config;

  @InjectMocks
  private PackagerCommand command;

  private CommandLine commandLine;

  @BeforeEach
  void setUp() {
    commandLine = new CommandLine(command);
  }

  @Test
  void shouldParseValidMinimalArguments() {
    // Given: Minimal valid arguments
    String[] args = {"--pseudonymizer-url", "http://localhost:8080"};

    // When: Parse arguments
    CommandLine.ParseResult result = commandLine.parseArgs(args);

    // Then: Arguments are parsed correctly
    assertThat(result.hasMatchedOption("--pseudonymizer-url")).isTrue();
    assertThat(command.getPseudonymizerUrl()).isEqualTo("http://localhost:8080");
    assertThat(command.getTimeoutSeconds()).isEqualTo(30); // default
    assertThat(command.getRetries()).isEqualTo(3); // default
    assertThat(command.isVerbose()).isFalse(); // default
    assertThat(command.getConfigFile()).isNull(); // default
  }

  @Test
  void shouldParseAllArguments() throws IOException {
    // Given: All CLI arguments with temporary config file
    Path tempFile = Files.createTempFile("config", ".yaml");
    Files.writeString(tempFile, "pseudonymizer:\n  url: http://test.com");
    
    try {
      String[] args = {
          "--pseudonymizer-url", "https://example.com:9090/pseudonymizer",
          "--timeout", "60",
          "--retries", "5",
          "--verbose",
          "--config-file", tempFile.toString()
      };

      // When: Parse arguments
      CommandLine.ParseResult result = commandLine.parseArgs(args);

      // Then: All arguments are parsed correctly
      assertThat(result.hasMatchedOption("--pseudonymizer-url")).isTrue();
      assertThat(result.hasMatchedOption("--timeout")).isTrue();
      assertThat(result.hasMatchedOption("--retries")).isTrue();
      assertThat(result.hasMatchedOption("--verbose")).isTrue();
      assertThat(result.hasMatchedOption("--config-file")).isTrue();

      assertThat(command.getPseudonymizerUrl()).isEqualTo("https://example.com:9090/pseudonymizer");
      assertThat(command.getTimeoutSeconds()).isEqualTo(60);
      assertThat(command.getRetries()).isEqualTo(5);
      assertThat(command.isVerbose()).isTrue();
      assertThat(command.getConfigFile()).isEqualTo(tempFile.toFile());
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  @Test
  void shouldParseShortFormArguments() throws IOException {
    // Given: Short form arguments with temporary config file
    Path tempFile = Files.createTempFile("config", ".yaml");
    
    try {
      String[] args = {
          "-u", "http://short.com",
          "-t", "45",
          "-r", "2",
          "-v",
          "-c", tempFile.toString()
      };

      // When: Parse arguments
      CommandLine.ParseResult result = commandLine.parseArgs(args);

      // Then: Short form arguments are parsed correctly
      assertThat(command.getPseudonymizerUrl()).isEqualTo("http://short.com");
      assertThat(command.getTimeoutSeconds()).isEqualTo(45);
      assertThat(command.getRetries()).isEqualTo(2);
      assertThat(command.isVerbose()).isTrue();
      assertThat(command.getConfigFile()).isEqualTo(tempFile.toFile());
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  @Test
  void shouldRejectInvalidTimeoutValue() {
    // Given: Invalid timeout value
    String[] args = {"--timeout", "0"};

    // When & Then: Execution should fail with constraint violation
    int exitCode = commandLine.execute(args);
    assertThat(exitCode).isEqualTo(2);
  }

  @Test
  void shouldRejectNegativeTimeoutValue() {
    // Given: Negative timeout value
    String[] args = {"--timeout", "-5"};

    // When & Then: Execution should fail with constraint violation
    int exitCode = commandLine.execute(args);
    assertThat(exitCode).isEqualTo(2);
  }

  @Test
  void shouldRejectNegativeRetries() {
    // Given: Negative retries value
    String[] args = {"--retries", "-1"};

    // When & Then: Execution should fail with constraint violation
    int exitCode = commandLine.execute(args);
    assertThat(exitCode).isEqualTo(2);
  }

  @Test
  void shouldAcceptZeroRetries() {
    // Given: Zero retries (valid minimum)
    String[] args = {"--retries", "0"};

    // When: Parse arguments
    CommandLine.ParseResult result = commandLine.parseArgs(args);

    // Then: Zero retries should be accepted
    assertThat(command.getRetries()).isEqualTo(0);
  }

  @Test
  void shouldCallCommandWithValidArguments() throws Exception {
    // Given: Valid configuration and arguments
    String[] args = {"--pseudonymizer-url", "http://localhost:8080"};
    commandLine.parseArgs(args);
    
    // When: Call command
    Integer result = command.call();

    // Then: Command should succeed
    assertThat(result).isEqualTo(0);
    verify(config).setUrl("http://localhost:8080");
  }

  @Test
  void shouldReturnErrorCodeForInvalidUrl() throws Exception {
    // Given: Invalid URL
    String[] args = {"--pseudonymizer-url", "invalid-url"};
    commandLine.parseArgs(args);

    // When: Call command
    Integer result = command.call();

    // Then: Should return invalid arguments error code
    assertThat(result).isEqualTo(2);
  }

  @Test
  void shouldReturnErrorCodeForNonExistentConfigFile() throws Exception {
    // Given: Non-existent config file
    String[] args = {"--config-file", "/non/existent/file.yaml"};
    commandLine.parseArgs(args);

    // When: Call command
    Integer result = command.call();

    // Then: Should return invalid arguments error code
    assertThat(result).isEqualTo(2);
  }

  @Test
  void shouldValidateHttpsUrls() throws Exception {
    // Given: HTTPS URL
    String[] args = {"--pseudonymizer-url", "https://secure.example.com/api"};
    commandLine.parseArgs(args);

    // When: Call command
    Integer result = command.call();

    // Then: Should succeed
    assertThat(result).isEqualTo(0);
    verify(config).setUrl("https://secure.example.com/api");
  }

  @Test
  void shouldRejectFtpUrls() throws Exception {
    // Given: FTP URL (unsupported protocol)
    String[] args = {"--pseudonymizer-url", "ftp://example.com/file"};
    commandLine.parseArgs(args);

    // When: Call command
    Integer result = command.call();

    // Then: Should return invalid arguments error code
    assertThat(result).isEqualTo(2);
  }

  @Test
  void shouldRejectUrlsWithoutProtocol() throws Exception {
    // Given: URL without protocol
    String[] args = {"--pseudonymizer-url", "example.com:8080"};
    commandLine.parseArgs(args);

    // When: Call command
    Integer result = command.call();

    // Then: Should return invalid arguments error code
    assertThat(result).isEqualTo(2);
  }

  @Test
  void shouldRejectUrlsWithoutHost() throws Exception {
    // Given: URL without host
    String[] args = {"--pseudonymizer-url", "http://:8080/path"};
    commandLine.parseArgs(args);

    // When: Call command
    Integer result = command.call();

    // Then: Should return invalid arguments error code
    assertThat(result).isEqualTo(2);
  }

  @Test
  void shouldApplyConfigOverrides() throws Exception {
    // Given: Custom values different from defaults
    String[] args = {
        "--pseudonymizer-url", "http://custom.com",
        "--timeout", "120",
        "--retries", "7"
    };
    commandLine.parseArgs(args);

    // When: Call command
    Integer result = command.call();

    // Then: Config should be updated with custom values
    assertThat(result).isEqualTo(0);
    verify(config).setUrl("http://custom.com");
    verify(config).setTimeout(Duration.ofSeconds(120));
    verify(config).setRetries(7);
  }

  @Test
  void shouldNotOverrideConfigWithDefaultValues() throws Exception {
    // Given: Custom URL and default timeout/retries
    String[] args = {"--pseudonymizer-url", "http://custom.example.com"};
    commandLine.parseArgs(args);

    // When: Call command
    Integer result = command.call();

    // Then: Only URL should be overridden, others should use config defaults
    assertThat(result).isEqualTo(0);
    verify(config).setUrl("http://custom.example.com");
    verify(config, never()).setTimeout(any());
    verify(config, never()).setRetries(anyInt());
  }

  @Test
  void shouldShowHelpMessage() {
    // Given: Help argument
    String[] args = {"--help"};

    // When: Parse arguments
    CommandLine.ParseResult result = commandLine.parseArgs(args);

    // Then: Help should be requested
    assertThat(result.isUsageHelpRequested()).isTrue();
  }

  @Test
  void shouldShowVersionMessage() {
    // Given: Version argument
    String[] args = {"--version"};

    // When: Parse arguments  
    CommandLine.ParseResult result = commandLine.parseArgs(args);

    // Then: Version should be requested
    assertThat(result.isVersionHelpRequested()).isTrue();
  }

  @Test
  void shouldValidateReadableConfigFile() throws Exception {
    // Given: Config file that exists but is not readable
    Path tempFile = Files.createTempFile("config", ".yaml");
    File file = tempFile.toFile();
    file.setReadable(false);
    
    try {
      String[] args = {"--config-file", tempFile.toString()};
      commandLine.parseArgs(args);

      // When: Call command
      Integer result = command.call();

      // Then: Should return error for unreadable file
      assertThat(result).isEqualTo(2);
    } finally {
      file.setReadable(true); // Restore for cleanup
      Files.deleteIfExists(tempFile);
    }
  }

  @Test
  void shouldRejectInvalidYamlSyntax() throws Exception {
    // Given: Config file with invalid YAML syntax
    URL resourceUrl = getClass().getClassLoader().getResource("invalid-yaml-syntax.yaml");
    assertThat(resourceUrl).isNotNull();
    
    String[] args = {"--config-file", resourceUrl.getPath()};
    commandLine.parseArgs(args);

    // When: Call command
    Integer result = command.call();

    // Then: Should return error for invalid YAML
    assertThat(result).isEqualTo(2);
  }

  @Test
  void shouldRejectInvalidUrlInConfigFile() throws Exception {
    // Given: Config file with invalid URL
    URL resourceUrl = getClass().getClassLoader().getResource("invalid-url-config.yaml");
    assertThat(resourceUrl).isNotNull();
    
    String[] args = {"--config-file", resourceUrl.getPath()};
    commandLine.parseArgs(args);

    // When: Call command
    Integer result = command.call();

    // Then: Should return error for invalid URL
    assertThat(result).isEqualTo(2);
  }

  @Test
  void shouldRejectInvalidTimeoutInConfigFile() throws Exception {
    // Given: Config file with invalid timeout format
    URL resourceUrl = getClass().getClassLoader().getResource("invalid-timeout-config.yaml");
    assertThat(resourceUrl).isNotNull();
    
    String[] args = {"--config-file", resourceUrl.getPath()};
    commandLine.parseArgs(args);

    // When: Call command
    Integer result = command.call();

    // Then: Should return error for invalid timeout
    assertThat(result).isEqualTo(2);
  }

  @Test
  void shouldRejectNegativeTimeoutInConfigFile() throws Exception {
    // Given: Config file with negative timeout
    URL resourceUrl = getClass().getClassLoader().getResource("negative-timeout-config.yaml");
    assertThat(resourceUrl).isNotNull();
    
    String[] args = {"--config-file", resourceUrl.getPath()};
    commandLine.parseArgs(args);

    // When: Call command
    Integer result = command.call();

    // Then: Should return error for negative timeout
    assertThat(result).isEqualTo(2);
  }

  @Test
  void shouldRejectNegativeRetriesInConfigFile() throws Exception {
    // Given: Config file with negative retries
    URL resourceUrl = getClass().getClassLoader().getResource("invalid-retries-config.yaml");
    assertThat(resourceUrl).isNotNull();
    
    String[] args = {"--config-file", resourceUrl.getPath()};
    commandLine.parseArgs(args);

    // When: Call command
    Integer result = command.call();

    // Then: Should return error for negative retries
    assertThat(result).isEqualTo(2);
  }

  @Test
  void shouldRejectInvalidDataTypesInConfigFile() throws Exception {
    // Given: Config file with wrong data types
    URL resourceUrl = getClass().getClassLoader().getResource("invalid-type-config.yaml");
    assertThat(resourceUrl).isNotNull();
    
    String[] args = {"--config-file", resourceUrl.getPath()};
    commandLine.parseArgs(args);

    // When: Call command
    Integer result = command.call();

    // Then: Should return error for wrong data types
    assertThat(result).isEqualTo(2);
  }

  @Test
  void shouldAcceptValidConfigFileFromResources() throws Exception {
    // Given: Valid config file
    URL resourceUrl = getClass().getClassLoader().getResource("valid-config.yaml");
    assertThat(resourceUrl).isNotNull();
    
    String[] args = {"--config-file", resourceUrl.getPath()};
    commandLine.parseArgs(args);

    // When: Call command
    Integer result = command.call();

    // Then: Should succeed with valid config file
    assertThat(result).isEqualTo(0);
  }

  @Test
  void shouldExpandTildeInConfigFilePath() throws IOException {
    // Given: Create a temporary config file in user home
    String userHome = System.getProperty("user.home");
    Path homeConfigFile = Paths.get(userHome, "test-config.yaml");
    
    try {
      // Create temporary config file in home directory
      Files.writeString(homeConfigFile, "pseudonymizer:\n  url: http://localhost:8080\n  timeout: PT30S\n  retries: 3");
      
      // Use tilde notation for the path
      String[] args = {"--config-file", "~/test-config.yaml"};
      
      // When: Parse arguments
      CommandLine.ParseResult result = commandLine.parseArgs(args);
      
      // Then: Tilde should be expanded to actual home directory
      assertThat(result.hasMatchedOption("--config-file")).isTrue();
      assertThat(command.getConfigFile()).isNotNull();
      assertThat(command.getConfigFile().getAbsolutePath()).isEqualTo(homeConfigFile.toAbsolutePath().toString());
      assertThat(command.getConfigFile().exists()).isTrue();
      
    } finally {
      // Clean up temporary file
      Files.deleteIfExists(homeConfigFile);
    }
  }

  @Test
  void shouldHandleTildeExpansionInValidation() throws Exception {
    // Given: Create a valid config file in user home for validation test
    String userHome = System.getProperty("user.home");
    Path homeConfigFile = Paths.get(userHome, "valid-test-config.yaml");
    
    try {
      // Create valid config file
      Files.writeString(homeConfigFile, 
          "pseudonymizer:\n" +
          "  url: https://example.com:8080\n" +
          "  timeout: PT45S\n" +
          "  retries: 5\n");
      
      String[] args = {"--config-file", "~/valid-test-config.yaml"};
      commandLine.parseArgs(args);
      
      // When: Call command (which includes validation)
      Integer result = command.call();
      
      // Then: Should succeed with expanded path
      assertThat(result).isEqualTo(0);
      
    } finally {
      // Clean up
      Files.deleteIfExists(homeConfigFile);
    }
  }
}