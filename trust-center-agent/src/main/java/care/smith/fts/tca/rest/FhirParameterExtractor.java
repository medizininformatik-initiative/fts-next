package care.smith.fts.tca.rest;

import java.util.List;
import lombok.experimental.UtilityClass;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.StringType;

/** Utility class for extracting and adding values to FHIR Parameters resources. */
@UtilityClass
public class FhirParameterExtractor {

  /**
   * Extracts a required string parameter from FHIR Parameters.
   *
   * @param params the FHIR Parameters resource
   * @param name the parameter name to extract
   * @return the parameter value
   * @throws IllegalArgumentException if the parameter is missing or empty
   */
  public static String extractRequiredString(Parameters params, String name) {
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
  public static List<String> extractAllStrings(Parameters params, String name) {
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
  public static List<String> extractRequiredStrings(Parameters params, String name) {
    List<String> values = extractAllStrings(params, name);
    if (values.isEmpty()) {
      throw new IllegalArgumentException("At least one '%s' parameter is required".formatted(name));
    }
    return values;
  }

  /**
   * Adds a string parameter to FHIR Parameters.
   *
   * @param params the FHIR Parameters resource to add to
   * @param name the parameter name
   * @param value the parameter value
   */
  public static void addParameter(Parameters params, String name, String value) {
    params.addParameter().setName(name).setValue(new StringType(value));
  }

  /**
   * Adds a string part to a ParametersParameterComponent.
   *
   * @param param the parameter component to add to
   * @param name the part name
   * @param value the part value
   */
  public static void addPart(ParametersParameterComponent param, String name, String value) {
    param.addPart().setName(name).setValue(new StringType(value));
  }
}
