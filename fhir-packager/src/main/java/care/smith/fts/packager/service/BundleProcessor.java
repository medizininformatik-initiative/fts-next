package care.smith.fts.packager.service;

import care.smith.fts.packager.config.PseudonymizerConfig;
import care.smith.fts.packager.service.BundleValidator.BundleValidationException;
import care.smith.fts.packager.service.BundleValidator.ValidationMode;
import care.smith.fts.util.fhir.FhirUtils;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Main orchestrator service for the complete FHIR Bundle processing pipeline.
 * 
 * <p>This service coordinates all phases of FHIR Bundle processing:
 * <ul>
 *   <li>Reading input from stdin</li>
 *   <li>Parsing and validating FHIR Bundle content</li>
 *   <li>Processing the bundle (currently identity transform, future: pseudonymization)</li>
 *   <li>Writing output to stdout</li>
 * </ul>
 * 
 * <p>The service handles all error conditions and provides appropriate exit codes:
 * <ul>
 *   <li>Exit code 1: Processing errors (I/O failures, exceptions)</li>
 *   <li>Exit code 3: Invalid FHIR Bundle (malformed JSON, not a Bundle, validation failures)</li>
 * </ul>
 * 
 * <p>All logging is performed to stderr to keep stdout clean for data output.
 */
@Slf4j
@Service
public class BundleProcessor {

  private final StdinReader stdinReader;
  private final StdoutWriter stdoutWriter;
  private final BundleValidator bundleValidator;
  private final PseudonymizerConfig config;
  private final PseudonymizerClient pseudonymizerClient;

  @Autowired
  public BundleProcessor(StdinReader stdinReader, 
                        StdoutWriter stdoutWriter,
                        BundleValidator bundleValidator,
                        PseudonymizerConfig config,
                        PseudonymizerClient pseudonymizerClient) {
    this.stdinReader = stdinReader;
    this.stdoutWriter = stdoutWriter;
    this.bundleValidator = bundleValidator;
    this.config = config;
    this.pseudonymizerClient = pseudonymizerClient;
  }

  /**
   * Custom exception for bundle processing failures that should result in exit code 3.
   */
  public static class BundleProcessingException extends Exception {
    public BundleProcessingException(String message) {
      super(message);
    }
    
    public BundleProcessingException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * Processes a FHIR Bundle through the complete pipeline.
   * 
   * <p>This method orchestrates the complete processing workflow:
   * <ol>
   *   <li>Read bundle data from stdin</li>
   *   <li>Parse and validate the bundle</li>
   *   <li>Process the bundle (currently identity transform)</li>
   *   <li>Write the result to stdout</li>
   * </ol>
   * 
   * @return exit code (0=success, 1=processing error, 3=invalid bundle)
   */
  public int processBundle() {
    log.info("Starting FHIR Bundle processing pipeline");
    
    try {
      // Step 1: Read from stdin
      String inputData = readFromStdin();
      
      // Step 2: Parse and validate bundle
      Bundle bundle = parseBundle(inputData);
      
      // Step 3: Validate bundle
      validateBundle(bundle);
      
      // Step 4: Process bundle (currently identity transform)
      Bundle processedBundle = processBundle(bundle);
      
      // Step 5: Write to stdout
      writeToStdout(processedBundle);
      
      log.info("FHIR Bundle processing completed successfully");
      return 0; // Success
      
    } catch (BundleProcessingException e) {
      log.error("Invalid FHIR Bundle: {}", e.getMessage());
      return 3; // Invalid bundle data
    } catch (Exception e) {
      log.error("Processing failed", e);
      return 1; // General processing error
    }
  }

  /**
   * Reads the complete input from standard input.
   * 
   * @return the input data as a string
   * @throws IOException if reading fails
   */
  public String readFromStdin() throws IOException {
    log.debug("Reading input from stdin");
    
    String input = stdinReader.readFromStdin();
    
    if (input.trim().isEmpty()) {
      throw new IOException("No input data received from stdin");
    }
    
    log.debug("Successfully read input data ({} characters)", input.length());
    return input;
  }

  /**
   * Parses a string as a FHIR Bundle.
   * 
   * @param bundleString the string to parse
   * @return the parsed Bundle
   * @throws BundleProcessingException if parsing fails
   */
  public Bundle parseBundle(String bundleString) throws BundleProcessingException {
    log.debug("Parsing FHIR Bundle from input string");
    
    if (bundleString == null || bundleString.trim().isEmpty()) {
      throw new BundleProcessingException("Bundle string is null or empty");
    }
    
    try {
      Bundle bundle = FhirUtils.stringToFhirBundle(bundleString);
      log.debug("Successfully parsed FHIR Bundle");
      return bundle;
    } catch (Exception e) {
      throw new BundleProcessingException("Failed to parse input as FHIR Bundle: " + e.getMessage(), e);
    }
  }

  /**
   * Validates a FHIR Bundle according to current configuration.
   * 
   * @param bundle the bundle to validate
   * @throws BundleProcessingException if validation fails
   */
  public void validateBundle(Bundle bundle) throws BundleProcessingException {
    log.debug("Validating FHIR Bundle");
    
    try {
      // Use strict validation mode for now
      // Future enhancement: make this configurable via PseudonymizerConfig
      ValidationMode validationMode = ValidationMode.STRICT;
      
      bundleValidator.validateBundle(bundle, validationMode);
      log.debug("FHIR Bundle validation completed successfully");
    } catch (BundleValidationException e) {
      throw new BundleProcessingException("Bundle validation failed: " + e.getMessage(), e);
    }
  }

  /**
   * Processes the FHIR Bundle by sending it to the pseudonymizer service.
   * 
   * <p>This method integrates with the PseudonymizerClient to perform actual
   * data pseudonymization. The client handles retry logic, error handling,
   * and network communication automatically.
   * 
   * @param bundle the bundle to process
   * @return the processed (pseudonymized) bundle
   * @throws BundleProcessingException if pseudonymization fails
   */
  public Bundle processBundle(Bundle bundle) throws BundleProcessingException {
    log.debug("Processing FHIR Bundle with pseudonymization service");
    
    try {
      // Phase 4: Send bundle to pseudonymizer service with retry logic
      log.info("Sending bundle to pseudonymizer service at: {}", config.url());
      
      // Perform health check if enabled
      if (config.healthCheckEnabled()) {
        log.debug("Performing health check before pseudonymization");
        PseudonymizerClient.HealthStatus healthStatus = pseudonymizerClient.checkHealth().block();
        
        if (healthStatus != null && !healthStatus.healthy()) {
          log.warn("Pseudonymizer service health check failed: {}", healthStatus.message());
          throw new BundleProcessingException("Pseudonymizer service is unhealthy: " + healthStatus.message());
        }
        
        if (healthStatus != null) {
          log.debug("Health check passed in {}ms", healthStatus.responseTimeMs());
        }
      }
      
      // Pseudonymize the bundle with automatic retry logic
      Bundle pseudonymizedBundle = pseudonymizerClient.pseudonymize(bundle).block();
      
      if (pseudonymizedBundle == null) {
        throw new BundleProcessingException("Pseudonymization returned null result");
      }
      
      log.info("Bundle pseudonymization completed successfully");
      log.debug("Bundle processing completed with pseudonymization applied");
      
      return pseudonymizedBundle;
      
    } catch (Exception e) {
      // Map different error types appropriately
      if (e.getCause() instanceof java.net.ConnectException) {
        throw new BundleProcessingException("Cannot connect to pseudonymizer service at " + config.url() + ": " + e.getMessage(), e);
      } else if (e.getCause() instanceof java.util.concurrent.TimeoutException) {
        throw new BundleProcessingException("Timeout connecting to pseudonymizer service: " + e.getMessage(), e);
      } else if (e instanceof BundleProcessingException) {
        throw e; // Re-throw bundle processing exceptions as-is
      } else {
        throw new BundleProcessingException("Pseudonymization failed: " + e.getMessage(), e);
      }
    }
  }

  /**
   * Writes a FHIR Bundle to standard output.
   * 
   * @param bundle the bundle to write
   * @throws IOException if writing fails
   */
  public void writeToStdout(Bundle bundle) throws IOException {
    log.debug("Writing FHIR Bundle to stdout");
    
    String bundleJson = FhirUtils.fhirResourceToString(bundle);
    stdoutWriter.writeToStdout(bundleJson);
    
    log.debug("Successfully wrote bundle to stdout");
  }
}