package care.smith.fts.tca.rest;

import java.util.List;
import java.util.regex.Pattern;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;

/** Utility interface for extracting and adding values to FHIR Parameters resources. */
public interface FhirParameterExtractor {

  /** Pattern for safe identifier strings: word characters and hyphens only. */
  Pattern SAFE_IDENTIFIER_PATTERN = Pattern.compile("^[\\w-]+$");

  /**
   * Extracts a required string parameter from FHIR Parameters.
   *
   * @param params the FHIR Parameters resource
   * @param name the parameter name to extract
   * @return the parameter value
   * @throws IllegalArgumentException if the parameter is missing or empty
   */
  static String extractRequiredString(Parameters params, String name) {
    String value =
        params.getParameter().stream()
            .filter(p -> name.equals(p.getName()))
            .findFirst()
            .map(ParametersParameterComponent::getValue)
            .map(Base::primitiveValue)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "Missing required parameter '%s'".formatted(name)));

    if (value.isBlank()) {
      throw new IllegalArgumentException("Parameter '%s' must not be empty".formatted(name));
    }

    return value;
  }

  /**
   * Extracts all string values for a given parameter name from FHIR Parameters.
   *
   * @param params the FHIR Parameters resource
   * @param name the parameter name to extract
   * @return list of parameter values (may be empty)
   */
  static List<String> extractAllStrings(Parameters params, String name) {
    return params.getParameter().stream()
        .filter(p -> name.equals(p.getName()))
        .map(ParametersParameterComponent::getValue)
        .map(Base::primitiveValue)
        .toList();
  }

  /**
   * Extracts all string values for a given parameter name, requiring at least one value.
   *
   * @param params the FHIR Parameters resource
   * @param name the parameter name to extract
   * @return list of parameter values (non-empty)
   * @throws IllegalArgumentException if no values are found
   */
  static List<String> extractRequiredStrings(Parameters params, String name) {
    List<String> values = extractAllStrings(params, name);
    if (values.isEmpty()) {
      throw new IllegalArgumentException("At least one '%s' parameter is required".formatted(name));
    }
    return values;
  }

  /**
   * Validates that a string is a safe identifier (word characters and hyphens only).
   *
   * @param value the value to validate
   * @param paramName the parameter name for error messages
   * @return the validated value
   * @throws IllegalArgumentException if the value is invalid
   */
  static String validateIdentifier(String value, String paramName) {
    if (!SAFE_IDENTIFIER_PATTERN.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "Parameter '%s' contains invalid characters".formatted(paramName));
    }
    return value;
  }

  /**
   * Validates a list of strings as safe identifiers.
   *
   * @param values the values to validate
   * @param paramName the parameter name for error messages
   * @return the validated values
   * @throws IllegalArgumentException if any value is invalid
   */
  static List<String> validateIdentifiers(List<String> values, String paramName) {
    for (String value : values) {
      validateIdentifier(value, paramName);
    }
    return values;
  }
}
