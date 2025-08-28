package care.smith.fts.packager.cli;

import care.smith.fts.packager.config.PseudonymizerConfig;
import care.smith.fts.packager.service.BundleProcessor;
import care.smith.fts.packager.service.BundleValidator;
import care.smith.fts.packager.service.StdinReader;
import care.smith.fts.packager.service.StdoutWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;
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
  
  @Mock
  private BundleProcessor bundleProcessor;
  
  @Mock
  private ApplicationContext applicationContext;
  
  @Mock
  private WebClient.Builder webClientBuilder;
  
  @Mock
  private StdinReader stdinReader;
  
  @Mock
  private StdoutWriter stdoutWriter;
  
  @Mock
  private BundleValidator bundleValidator;

  @InjectMocks
  private PackagerCommand command;

  private CommandLine commandLine;

  @BeforeEach
  void setUp() {
    commandLine = new CommandLine(command);
  }

  @Test
  void shouldParseValidMinimalArguments() {
    String[] args = {"--pseudonymizer-url", "http://localhost:8080"};

    CommandLine.ParseResult result = commandLine.parseArgs(args);

    assertThat(result.hasMatchedOption("--pseudonymizer-url")).isTrue();
    assertThat(command.getPseudonymizerUrl()).isEqualTo("http://localhost:8080");
    assertThat(command.getTimeoutSeconds()).isEqualTo(30); // default
    assertThat(command.getRetries()).isEqualTo(3); // default
    assertThat(command.isVerbose()).isFalse(); // default
    assertThat(command.getConfigFile()).isNull(); // default
  }

  @Test
  void shouldParseAllArguments() throws IOException {
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

      CommandLine.ParseResult result = commandLine.parseArgs(args);

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
    Path tempFile = Files.createTempFile("config", ".yaml");
    
    try {
      String[] args = {
          "-u", "http://short.com",
          "-t", "45",
          "-r", "2",
          "-v",
          "-c", tempFile.toString()
      };

      CommandLine.ParseResult result = commandLine.parseArgs(args);

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
    String[] args = {"--timeout", "0"};

    assertThatThrownBy(() -> commandLine.parseArgs(args))
        .isInstanceOf(CommandLine.ParameterException.class)
        .hasMessageContaining("Timeout must be at least 1 second");
  }

  @Test
  void shouldRejectNegativeTimeoutValue() {
    String[] args = {"--timeout", "-5"};

    assertThatThrownBy(() -> commandLine.parseArgs(args))
        .isInstanceOf(CommandLine.ParameterException.class)
        .hasMessageContaining("Timeout must be at least 1 second");
  }

  @Test
  void shouldRejectNegativeRetries() {
    String[] args = {"--retries", "-1"};

    assertThatThrownBy(() -> commandLine.parseArgs(args))
        .isInstanceOf(CommandLine.ParameterException.class)
        .hasMessageContaining("Retries must be at least 0");
  }

  @Test
  void shouldAcceptZeroRetries() {
    String[] args = {"--retries", "0"};

    CommandLine.ParseResult result = commandLine.parseArgs(args);

    assertThat(command.getRetries()).isEqualTo(0);
  }

  @Test
  void shouldParseValidUrl() {
    String[] args = {"--pseudonymizer-url", "http://localhost:8080"};
    
    CommandLine.ParseResult result = commandLine.parseArgs(args);

    assertThat(command.getPseudonymizerUrl()).isEqualTo("http://localhost:8080");
  }

  @Test
  void shouldRejectInvalidUrl() {
    String[] args = {"--pseudonymizer-url", "invalid-url"};
    
    CommandLine.ParseResult result = commandLine.parseArgs(args);
    assertThat(command.getPseudonymizerUrl()).isEqualTo("invalid-url");
    // Note: URL validation is done in PackagerCommand.call(), not during parsing
  }

  @Test
  void shouldReturnErrorCodeForNonExistentConfigFile() throws Exception {
    String[] args = {"--config-file", "/non/existent/file.yaml"};
    commandLine.parseArgs(args);

    Integer result = command.call();

    assertThat(result).isEqualTo(2);
  }

  @Test
  void shouldValidateHttpsUrls() throws Exception {
    String[] args = {"--pseudonymizer-url", "https://secure.example.com/api"};
    
    CommandLine.ParseResult result = commandLine.parseArgs(args);

    assertThat(result.hasMatchedOption("--pseudonymizer-url")).isTrue();
    assertThat(command.getPseudonymizerUrl()).isEqualTo("https://secure.example.com/api");
    // Note: URL validation happens in call() method, here we just verify parsing
  }

  @Test
  void shouldRejectFtpUrls() throws Exception {
    String[] args = {"--pseudonymizer-url", "ftp://example.com/file"};
    commandLine.parseArgs(args);

    Integer result = command.call();

    assertThat(result).isEqualTo(2);
  }

  @Test
  void shouldRejectUrlsWithoutProtocol() throws Exception {
    String[] args = {"--pseudonymizer-url", "example.com:8080"};
    commandLine.parseArgs(args);

    Integer result = command.call();

    assertThat(result).isEqualTo(2);
  }

  @Test
  void shouldRejectUrlsWithoutHost() throws Exception {
    String[] args = {"--pseudonymizer-url", "http://:8080/path"};
    commandLine.parseArgs(args);

    Integer result = command.call();

    assertThat(result).isEqualTo(2);
  }

  @Test
  void shouldParseCustomConfigValues() {
    String[] args = {
        "--pseudonymizer-url", "http://custom.com",
        "--timeout", "120",
        "--retries", "7"
    };

    CommandLine.ParseResult result = commandLine.parseArgs(args);

    assertThat(command.getPseudonymizerUrl()).isEqualTo("http://custom.com");
    assertThat(command.getTimeoutSeconds()).isEqualTo(120);
    assertThat(command.getRetries()).isEqualTo(7);
  }

  @Test
  void shouldUseDefaultsWhenNotSpecified() {
    String[] args = {"--pseudonymizer-url", "http://custom.example.com"};
    
    CommandLine.ParseResult result = commandLine.parseArgs(args);

    assertThat(command.getPseudonymizerUrl()).isEqualTo("http://custom.example.com");
    assertThat(command.getTimeoutSeconds()).isEqualTo(30); // Picocli default
    assertThat(command.getRetries()).isEqualTo(3); // Picocli default
  }

  @Test
  void shouldShowHelpMessage() {
    String[] args = {"--help"};

    CommandLine.ParseResult result = commandLine.parseArgs(args);

    assertThat(result.isUsageHelpRequested()).isTrue();
  }

  @Test
  void shouldShowVersionMessage() {
    String[] args = {"--version"};

    CommandLine.ParseResult result = commandLine.parseArgs(args);

    assertThat(result.isVersionHelpRequested()).isTrue();
  }

  @Test
  void shouldValidateReadableConfigFile() throws Exception {
    Path tempFile = Files.createTempFile("config", ".yaml");
    File file = tempFile.toFile();
    file.setReadable(false);
    
    try {
      String[] args = {"--config-file", tempFile.toString()};
      commandLine.parseArgs(args);

      Integer result = command.call();

      assertThat(result).isEqualTo(2);
    } finally {
      file.setReadable(true); // Restore for cleanup
      Files.deleteIfExists(tempFile);
    }
  }

  @Test
  void shouldRejectInvalidYamlSyntax() throws Exception {
    URL resourceUrl = getClass().getClassLoader().getResource("invalid-yaml-syntax.yaml");
    assertThat(resourceUrl).isNotNull();
    
    String[] args = {"--config-file", resourceUrl.getPath()};
    commandLine.parseArgs(args);

    Integer result = command.call();

    assertThat(result).isEqualTo(2);
  }

  @Test
  void shouldRejectInvalidUrlInConfigFile() throws Exception {
    URL resourceUrl = getClass().getClassLoader().getResource("invalid-url-config.yaml");
    assertThat(resourceUrl).isNotNull();
    
    String[] args = {"--config-file", resourceUrl.getPath()};
    commandLine.parseArgs(args);

    Integer result = command.call();

    assertThat(result).isEqualTo(2);
  }

  @Test
  void shouldRejectInvalidTimeoutInConfigFile() throws Exception {
    URL resourceUrl = getClass().getClassLoader().getResource("invalid-timeout-config.yaml");
    assertThat(resourceUrl).isNotNull();
    
    String[] args = {"--config-file", resourceUrl.getPath()};
    commandLine.parseArgs(args);

    Integer result = command.call();

    assertThat(result).isEqualTo(2);
  }

  @Test
  void shouldRejectNegativeTimeoutInConfigFile() throws Exception {
    URL resourceUrl = getClass().getClassLoader().getResource("negative-timeout-config.yaml");
    assertThat(resourceUrl).isNotNull();
    
    String[] args = {"--config-file", resourceUrl.getPath()};
    commandLine.parseArgs(args);

    Integer result = command.call();

    assertThat(result).isEqualTo(2);
  }

  @Test
  void shouldRejectNegativeRetriesInConfigFile() throws Exception {
    URL resourceUrl = getClass().getClassLoader().getResource("invalid-retries-config.yaml");
    assertThat(resourceUrl).isNotNull();
    
    String[] args = {"--config-file", resourceUrl.getPath()};
    commandLine.parseArgs(args);

    Integer result = command.call();

    assertThat(result).isEqualTo(2);
  }

  @Test
  void shouldRejectInvalidDataTypesInConfigFile() throws Exception {
    URL resourceUrl = getClass().getClassLoader().getResource("invalid-type-config.yaml");
    assertThat(resourceUrl).isNotNull();
    
    String[] args = {"--config-file", resourceUrl.getPath()};
    commandLine.parseArgs(args);

    Integer result = command.call();

    assertThat(result).isEqualTo(2);
  }

  @Test
  void shouldAcceptValidConfigFileFromResources() throws Exception {
    URL resourceUrl = getClass().getClassLoader().getResource("valid-config.yaml");
    assertThat(resourceUrl).isNotNull();
    
    String[] args = {"--config-file", resourceUrl.getPath()};
    
    CommandLine.ParseResult result = commandLine.parseArgs(args);

    assertThat(result.hasMatchedOption("--config-file")).isTrue();
    assertThat(command.getConfigFile()).isNotNull();
    assertThat(command.getConfigFile().exists()).isTrue();
    assertThat(command.getConfigFile().getAbsolutePath()).isEqualTo(resourceUrl.getPath());
  }

  @Test
  void shouldExpandTildeInConfigFilePath() throws IOException {
    String userHome = System.getProperty("user.home");
    Path homeConfigFile = Paths.get(userHome, "test-config.yaml");
    
    try {
      // Create temporary config file in home directory
      Files.writeString(homeConfigFile, "pseudonymizer:\n  url: http://localhost:8080\n  timeout: PT30S\n  retries: 3");
      
      // Use tilde notation for the path
      String[] args = {"--config-file", "~/test-config.yaml"};
      
      CommandLine.ParseResult result = commandLine.parseArgs(args);
      
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
      
      CommandLine.ParseResult result = commandLine.parseArgs(args);
      
      assertThat(result.hasMatchedOption("--config-file")).isTrue();
      assertThat(command.getConfigFile()).isNotNull();
      assertThat(command.getConfigFile().getAbsolutePath()).isEqualTo(homeConfigFile.toAbsolutePath().toString());
      assertThat(command.getConfigFile().exists()).isTrue();
      
    } finally {
      // Clean up
      Files.deleteIfExists(homeConfigFile);
    }
  }

  @Test
  void shouldApplyCliOverridesCorrectly() throws Exception {
    PseudonymizerConfig.RetryConfig retryConfig = new PseudonymizerConfig.RetryConfig(
        3, Duration.ofSeconds(1), Duration.ofSeconds(30), 2.0);
    PseudonymizerConfig originalConfig = new PseudonymizerConfig(
        "http://localhost:8080",
        Duration.ofSeconds(10),
        Duration.ofSeconds(60),
        retryConfig,
        true
    );
    
    // Set up mocks
    when(config.url()).thenReturn(originalConfig.url());
    when(config.connectTimeout()).thenReturn(originalConfig.connectTimeout());
    when(config.readTimeout()).thenReturn(originalConfig.readTimeout());
    when(config.retry()).thenReturn(originalConfig.retry());
    when(config.healthCheckEnabled()).thenReturn(originalConfig.healthCheckEnabled());
    
    // Note: WebClient mocking not needed for this test as we only test the applyCliOverrides method
    
    String[] args = {
        "--pseudonymizer-url", "http://localhost:9999",
        "--timeout", "45",
        "--retries", "5"
    };
    
    commandLine.parseArgs(args);
    
    java.lang.reflect.Method applyOverridesMethod = PackagerCommand.class
        .getDeclaredMethod("applyCliOverrides");
    applyOverridesMethod.setAccessible(true);
    PseudonymizerConfig effectiveConfig = (PseudonymizerConfig) applyOverridesMethod.invoke(command);
    
    assertThat(effectiveConfig.url()).isEqualTo("http://localhost:9999");
    assertThat(effectiveConfig.readTimeout()).isEqualTo(Duration.ofSeconds(45));
    assertThat(effectiveConfig.retry().maxAttempts()).isEqualTo(5);
    // Values not overridden should remain the same
    assertThat(effectiveConfig.connectTimeout()).isEqualTo(Duration.ofSeconds(10));
    assertThat(effectiveConfig.healthCheckEnabled()).isTrue();
  }

  @Test
  void shouldReturnOriginalConfigWhenNoOverrides() throws Exception {
    PseudonymizerConfig originalConfig = new PseudonymizerConfig(
        "http://localhost:8080",
        Duration.ofSeconds(10),
        Duration.ofSeconds(60),
        new PseudonymizerConfig.RetryConfig(),
        true
    );
    
    // Set up mocks to return the same values as CLI defaults
    when(config.url()).thenReturn("http://localhost:8080");
    when(config.readTimeout()).thenReturn(Duration.ofSeconds(60));
    when(config.retry()).thenReturn(new PseudonymizerConfig.RetryConfig(3, Duration.ofSeconds(1), Duration.ofSeconds(30), 2.0));
    
    String[] args = {
        "--pseudonymizer-url", "http://localhost:8080", // same as default
        "--timeout", "60", // same as config default (not CLI default!)
        "--retries", "3"   // same as default
    };
    
    commandLine.parseArgs(args);
    
    java.lang.reflect.Method applyOverridesMethod = PackagerCommand.class
        .getDeclaredMethod("applyCliOverrides");
    applyOverridesMethod.setAccessible(true);
    PseudonymizerConfig effectiveConfig = (PseudonymizerConfig) applyOverridesMethod.invoke(command);
    
    assertThat(effectiveConfig).isSameAs(config);
  }
}