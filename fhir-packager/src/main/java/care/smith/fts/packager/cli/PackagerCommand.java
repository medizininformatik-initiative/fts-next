package care.smith.fts.packager.cli;

import care.smith.fts.packager.config.PseudonymizerConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
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
   */
  @Option(
      names = {"--config-file", "-c"},
      description = "Path to external configuration file"
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
    
    log.debug("All CLI arguments validated successfully");
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