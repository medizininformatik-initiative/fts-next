package care.smith.fts.packager.cli;

import care.smith.fts.packager.config.PseudonymizerConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Picocli command implementation for the FHIR Packager CLI tool.
 *
 * <p>This class defines all CLI options, handles argument parsing and validation,
 * and orchestrates the packaging process. It integrates with Spring Boot configuration
 * to support multiple configuration sources (CLI args, environment variables, application.yaml).
 *
 * <p>Exit codes:
 * <ul>
 *   <li>0: Success</li>
 *   <li>1: General error (processing failure, network error)</li>
 *   <li>2: Invalid arguments or configuration</li>
 * </ul>
 */
@Slf4j
@Component
@Command(
    name = "fhir-packager",
    description = "Pseudonymizes FHIR Bundles using an external FHIR Pseudonymizer service.",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    exitCodeOnInvalidInput = 2,
    exitCodeOnExecutionException = 1
)
@Getter
public class PackagerCommand implements Callable<Integer> {

  @Autowired
  private PseudonymizerConfig config;

  /**
   * URL of the FHIR Pseudonymizer service endpoint.
   */
  @Option(
      names = {"--pseudonymizer-url", "-u"},
      description = "URL of the FHIR Pseudonymizer service (default: ${DEFAULT-VALUE})",
      defaultValue = "http://localhost:8080"
  )
  private String pseudonymizerUrl;

  /**
   * Request timeout in seconds for HTTP calls to the pseudonymizer service.
   */
  @Option(
      names = {"--timeout", "-t"},
      description = "Request timeout in seconds (default: ${DEFAULT-VALUE})",
      defaultValue = "30"
  )
  private int timeoutSeconds;

  /**
   * Number of retry attempts for failed requests.
   */
  @Option(
      names = {"--retries", "-r"},
      description = "Number of retry attempts for failed requests (default: ${DEFAULT-VALUE})",
      defaultValue = "3"
  )
  private int retries;

  /**
   * Enable verbose logging for debugging purposes.
   */
  @Option(
      names = {"--verbose", "-v"},
      description = "Enable verbose logging for debugging"
  )
  private boolean verbose;

  /**
   * External configuration file path for additional settings.
   * 
   * <p>Supports tilde expansion (~/path/to/file) for user home directory.
   */
  @Option(
      names = {"--config-file", "-c"},
      description = "Path to external configuration file (supports ~/home/path)",
      converter = TildeExpandingFileConverter.class
  )
  private File configFile;

  /**
   * Main command execution method called by Picocli.
   *
   * <p>This method validates arguments, applies configuration overrides,
   * and orchestrates the FHIR Bundle processing pipeline.
   *
   * @return exit code (0=success, 1=error, 2=invalid args)
   * @throws Exception if processing fails
   */
  @Override
  public Integer call() throws Exception {
    log.info("FHIR Packager starting with pseudonymizer URL: {}", pseudonymizerUrl);
    
    if (verbose) {
      log.info("Verbose logging enabled");
      // Set root logger to DEBUG level
      ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME))
          .setLevel(ch.qos.logback.classic.Level.DEBUG);
    }

    try {
      // Validate arguments
      validateArguments();
      
      // Apply CLI overrides to configuration
      applyCliOverrides();
      
      // Phase 2: Just validate and log the configuration
      // Future phases will implement:
      // - FHIR Bundle processing from stdin
      // - Pseudonymizer service integration
      // - Bundle output to stdout
      
      log.info("Configuration validated successfully");
      log.info("Pseudonymizer URL: {}", config.getUrl());
      log.info("Timeout: {}", config.getTimeout());
      log.info("Retries: {}", config.getRetries());
      
      if (configFile != null) {
        log.info("Config file specified: {}", configFile.getAbsolutePath());
      }
      
      return 0; // Success
      
    } catch (IllegalArgumentException e) {
      log.error("Invalid argument: {}", e.getMessage());
      return 2; // Invalid arguments
    } catch (Exception e) {
      log.error("Processing failed", e);
      return 1; // General error
    }
  }

  /**
   * Validates CLI arguments for correctness and consistency.
   *
   * @throws IllegalArgumentException if any argument is invalid
   */
  private void validateArguments() {
    // Validate pseudonymizer URL format
    validateUrl(pseudonymizerUrl, "pseudonymizer-url");
    
    // Validate timeout range
    if (timeoutSeconds < 1) {
      throw new IllegalArgumentException("Timeout must be at least 1 second, got: " + timeoutSeconds);
    }
    
    // Validate retries range
    if (retries < 0) {
      throw new IllegalArgumentException("Retries must be at least 0, got: " + retries);
    }
    
    // Validate config file exists if specified
    if (configFile != null && !configFile.exists()) {
      throw new IllegalArgumentException("Config file does not exist: " + configFile.getAbsolutePath());
    }
    
    // Validate config file is readable if specified
    if (configFile != null && !configFile.canRead()) {
      throw new IllegalArgumentException("Config file is not readable: " + configFile.getAbsolutePath());
    }
    
    // Validate config file contents if specified
    if (configFile != null) {
      validateConfigFile();
    }
    
    log.debug("All CLI arguments validated successfully");
  }

  /**
   * Validates the contents of the external configuration file.
   * 
   * <p>This method parses the YAML configuration file and validates:
   * <ul>
   *   <li>YAML syntax is correct</li>
   *   <li>Configuration structure is valid</li>
   *   <li>Property values are within expected ranges</li>
   *   <li>URLs have correct format</li>
   * </ul>
   *
   * @throws IllegalArgumentException if config file contents are invalid
   */
  private void validateConfigFile() {
    try {
      ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
      
      // Parse the YAML file into a generic map structure
      Map<String, Object> configData = yamlMapper.readValue(configFile, Map.class);
      log.debug("Successfully parsed config file: {}", configFile.getAbsolutePath());
      
      // Validate pseudonymizer configuration section if present
      if (configData.containsKey("pseudonymizer")) {
        validatePseudonymizerSection(configData.get("pseudonymizer"));
      }
      
      log.debug("Config file validation completed successfully");
      
    } catch (IOException e) {
      throw new IllegalArgumentException(
          "Invalid YAML syntax in config file '" + configFile.getAbsolutePath() + "': " + e.getMessage(), e);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Config file validation failed for '" + configFile.getAbsolutePath() + "': " + e.getMessage(), e);
    }
  }
  
  /**
   * Validates the pseudonymizer configuration section from the config file.
   *
   * @param pseudonymizerSection the pseudonymizer configuration object
   * @throws IllegalArgumentException if any configuration is invalid
   */
  @SuppressWarnings("unchecked")
  private void validatePseudonymizerSection(Object pseudonymizerSection) {
    if (!(pseudonymizerSection instanceof Map)) {
      throw new IllegalArgumentException("Pseudonymizer configuration must be an object/map");
    }
    
    Map<String, Object> config = (Map<String, Object>) pseudonymizerSection;
    
    // Validate URL if present
    if (config.containsKey("url")) {
      Object urlValue = config.get("url");
      if (!(urlValue instanceof String)) {
        throw new IllegalArgumentException("Pseudonymizer URL must be a string");
      }
      try {
        validateUrl((String) urlValue, "pseudonymizer.url");
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Invalid pseudonymizer.url in config file: " + e.getMessage());
      }
    }
    
    // Validate timeout if present
    if (config.containsKey("timeout")) {
      Object timeoutValue = config.get("timeout");
      if (timeoutValue instanceof String) {
        try {
          Duration timeout = Duration.parse((String) timeoutValue);
          if (timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Pseudonymizer timeout must be positive");
          }
        } catch (Exception e) {
          throw new IllegalArgumentException("Invalid pseudonymizer.timeout format in config file (expected ISO-8601 duration like 'PT30S'): " + e.getMessage());
        }
      } else if (timeoutValue instanceof Number) {
        // Handle numeric timeout values (seconds)
        long timeoutSeconds = ((Number) timeoutValue).longValue();
        if (timeoutSeconds < 1) {
          throw new IllegalArgumentException("Pseudonymizer timeout must be at least 1 second");
        }
      } else {
        throw new IllegalArgumentException("Pseudonymizer timeout must be a string (ISO-8601 duration) or number (seconds)");
      }
    }
    
    // Validate retries if present
    if (config.containsKey("retries")) {
      Object retriesValue = config.get("retries");
      if (!(retriesValue instanceof Number)) {
        throw new IllegalArgumentException("Pseudonymizer retries must be a number");
      }
      int retries = ((Number) retriesValue).intValue();
      if (retries < 0) {
        throw new IllegalArgumentException("Pseudonymizer retries must be at least 0");
      }
    }
  }

  /**
   * Validates that a string is a valid URL.
   *
   * @param urlString the URL string to validate
   * @param optionName the name of the CLI option for error messages
   * @throws IllegalArgumentException if URL is invalid
   */
  private void validateUrl(String urlString, String optionName) {
    try {
      URI uri = new URI(urlString);
      URL url = uri.toURL();
      
      // Check for required components
      if (url.getProtocol() == null) {
        throw new IllegalArgumentException("Missing protocol in " + optionName + ": " + urlString);
      }
      if (url.getHost() == null || url.getHost().trim().isEmpty()) {
        throw new IllegalArgumentException("Missing host in " + optionName + ": " + urlString);
      }
      
      // Validate protocol is HTTP or HTTPS
      String protocol = url.getProtocol().toLowerCase();
      if (!"http".equals(protocol) && !"https".equals(protocol)) {
        throw new IllegalArgumentException("Unsupported protocol in " + optionName + " (must be http or https): " + urlString);
      }
      
    } catch (URISyntaxException | MalformedURLException e) {
      throw new IllegalArgumentException("Invalid URL format in " + optionName + ": " + urlString, e);
    }
  }

  /**
   * Applies CLI argument overrides to the Spring configuration.
   * CLI arguments take precedence over configuration file values.
   */
  private void applyCliOverrides() {
    // Always override pseudonymizer URL with CLI value (even if it's the default)
    config.setUrl(pseudonymizerUrl);
    log.debug("Applied CLI override for pseudonymizer URL: {}", pseudonymizerUrl);
    
    // Override timeout if provided via CLI and different from default
    if (timeoutSeconds != 30) {
      config.setTimeout(Duration.ofSeconds(timeoutSeconds));
      log.debug("Applied CLI override for timeout: {} seconds", timeoutSeconds);
    }
    
    // Override retries if provided via CLI and different from default
    if (retries != 3) {
      config.setRetries(retries);
      log.debug("Applied CLI override for retries: {}", retries);
    }
  }
}