package care.smith.fts.packager.service;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.DataFormatException;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.stereotype.Service;

/**
 * Service for validating FHIR Bundle structure and content.
 * 
 * <p>This service performs validation of FHIR Bundle resources to ensure they
 * meet the required structure and content requirements. It supports both strict
 * and lenient validation modes and provides clear error messages for validation
 * failures.
 * 
 * <p>Validation checks include:
 * <ul>
 *   <li>Bundle type is present and valid</li>
 *   <li>Bundle contains at least one entry</li>
 *   <li>All entries have valid resource types</li>
 *   <li>Bundle metadata is present where required</li>
 * </ul>
 */
@Slf4j
@Service
public class BundleValidator {

  private static final FhirContext fhirContext = FhirContext.forR4();

  /**
   * Validation mode for bundle validation.
   */
  public enum ValidationMode {
    /**
     * Strict validation - all requirements must be met.
     */
    STRICT,
    
    /**
     * Lenient validation - some requirements may be relaxed.
     */
    LENIENT
  }

  /**
   * Custom exception for bundle validation failures.
   */
  public static class BundleValidationException extends Exception {
    public BundleValidationException(String message) {
      super(message);
    }
    
    public BundleValidationException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /**
   * Validates a FHIR Bundle according to the specified validation mode.
   * 
   * @param bundle the Bundle to validate
   * @param mode the validation mode (strict or lenient)
   * @throws BundleValidationException if the bundle is invalid
   */
  public void validateBundle(Bundle bundle, ValidationMode mode) throws BundleValidationException {
    log.debug("Starting bundle validation in {} mode", mode);
    
    if (bundle == null) {
      throw new BundleValidationException("Bundle cannot be null");
    }
    
    validateBundleType(bundle, mode);
    validateBundleEntries(bundle, mode);
    validateBundleResources(bundle, mode);
    
    log.debug("Bundle validation completed successfully");
  }

  /**
   * Validates the bundle type is present and appropriate.
   * 
   * @param bundle the Bundle to validate
   * @param mode the validation mode
   * @throws BundleValidationException if the bundle type is invalid
   */
  private void validateBundleType(Bundle bundle, ValidationMode mode) throws BundleValidationException {
    Bundle.BundleType bundleType = bundle.getType();
    
    if (bundleType == null) {
      if (mode == ValidationMode.STRICT) {
        throw new BundleValidationException("Bundle type is required but was null");
      } else {
        log.warn("Bundle type is null - allowed in lenient mode");
      }
    } else {
      log.debug("Bundle type: {}", bundleType);
      
      // In strict mode, validate that the bundle type is appropriate for processing
      if (mode == ValidationMode.STRICT) {
        validateBundleTypeForProcessing(bundleType);
      }
    }
  }

  /**
   * Validates that the bundle type is appropriate for processing.
   * 
   * @param bundleType the bundle type to validate
   * @throws BundleValidationException if the bundle type is not suitable for processing
   */
  private void validateBundleTypeForProcessing(Bundle.BundleType bundleType) 
      throws BundleValidationException {
    
    // Allow common bundle types that are suitable for processing
    switch (bundleType) {
      case COLLECTION:
      case TRANSACTION:
      case BATCH:
      case DOCUMENT:
      case SEARCHSET:
        log.debug("Bundle type {} is valid for processing", bundleType);
        break;
      case MESSAGE:
      case HISTORY:
        log.warn("Bundle type {} may not be suitable for standard processing", bundleType);
        break;
      case NULL:
        throw new BundleValidationException("Bundle type cannot be NULL");
      default:
        log.warn("Unknown or unsupported bundle type: {}", bundleType);
        break;
    }
  }

  /**
   * Validates that the bundle contains entries.
   * 
   * @param bundle the Bundle to validate
   * @param mode the validation mode
   * @throws BundleValidationException if the bundle entries are invalid
   */
  private void validateBundleEntries(Bundle bundle, ValidationMode mode) throws BundleValidationException {
    if (bundle.getEntry() == null || bundle.getEntry().isEmpty()) {
      String message = "Bundle contains no entries";
      
      if (mode == ValidationMode.STRICT) {
        throw new BundleValidationException(message);
      } else {
        log.warn("{} - allowed in lenient mode", message);
      }
    } else {
      log.debug("Bundle contains {} entries", bundle.getEntry().size());
    }
  }

  /**
   * Validates the resources within the bundle entries.
   * 
   * @param bundle the Bundle to validate
   * @param mode the validation mode
   * @throws BundleValidationException if the bundle resources are invalid
   */
  private void validateBundleResources(Bundle bundle, ValidationMode mode) 
      throws BundleValidationException {
    
    if (bundle.getEntry() == null) {
      return; // Already handled in validateBundleEntries
    }
    
    int entryCount = 0;
    int resourceCount = 0;
    String firstNullResourceMessage = null;
    
    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      entryCount++;
      
      if (entry.getResource() == null) {
        String message = String.format("Entry %d contains no resource", entryCount);
        
        if (mode == ValidationMode.STRICT && firstNullResourceMessage == null) {
          // Remember the first null resource message, but don't throw yet
          firstNullResourceMessage = message;
        } else if (mode == ValidationMode.LENIENT) {
          log.warn("{} - allowed in lenient mode", message);
        }
      } else {
        resourceCount++;
        validateResource(entry.getResource(), entryCount, mode);
      }
    }
    
    log.debug("Validated {} entries with {} resources", entryCount, resourceCount);
    
    if (mode == ValidationMode.STRICT) {
      if (resourceCount == 0) {
        // If no valid resources at all:
        // - Single entry with null resource: report specific entry
        // - Multiple entries all null: report general message
        if (entryCount == 1 && firstNullResourceMessage != null) {
          throw new BundleValidationException(firstNullResourceMessage);
        } else {
          throw new BundleValidationException("Bundle contains no valid resources");
        }
      } else if (firstNullResourceMessage != null) {
        // If some entries have null resources but others are valid, report the first null
        throw new BundleValidationException(firstNullResourceMessage);
      }
    }
  }

  /**
   * Validates an individual resource within a bundle entry.
   * 
   * @param resource the resource to validate
   * @param entryIndex the index of the entry (for error messages)
   * @param mode the validation mode
   * @throws BundleValidationException if the resource is invalid
   */
  private void validateResource(org.hl7.fhir.r4.model.Resource resource, 
                              int entryIndex, 
                              ValidationMode mode) throws BundleValidationException {
    
    if (resource.getResourceType() == null) {
      String message = String.format("Resource in entry %d has no resource type", entryIndex);
      
      if (mode == ValidationMode.STRICT) {
        throw new BundleValidationException(message);
      } else {
        log.warn("{} - allowed in lenient mode", message);
      }
    }
    
    // Additional resource validation could be added here
    // For example: validate required fields, business rules, etc.
    
    log.debug("Entry {}: valid {} resource", entryIndex, resource.getResourceType());
  }

  /**
   * Validates that a string can be parsed as a valid FHIR Bundle.
   * 
   * @param bundleString the string to validate as a FHIR Bundle
   * @param mode the validation mode
   * @return the parsed Bundle if valid
   * @throws BundleValidationException if the string cannot be parsed as a valid Bundle
   */
  public Bundle validateAndParseBundle(String bundleString, ValidationMode mode) 
      throws BundleValidationException {
    
    if (bundleString == null || bundleString.trim().isEmpty()) {
      throw new BundleValidationException("Bundle string is null or empty");
    }
    
    log.debug("Parsing bundle string ({} characters)", bundleString.length());
    
    Bundle bundle;
    try {
      bundle = fhirContext.newJsonParser().parseResource(Bundle.class, bundleString);
    } catch (DataFormatException e) {
      throw new BundleValidationException("Failed to parse bundle as valid FHIR JSON: " + e.getMessage(), e);
    } catch (Exception e) {
      throw new BundleValidationException("Unexpected error parsing bundle: " + e.getMessage(), e);
    }
    
    // Validate the parsed bundle
    validateBundle(bundle, mode);
    
    return bundle;
  }
}