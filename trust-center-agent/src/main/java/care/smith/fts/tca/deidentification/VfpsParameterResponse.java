package care.smith.fts.tca.deidentification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Record to deserialize Vfps response from $create-pseudonym operation.
 *
 * <p>Response format:
 *
 * <pre>
 * {
 *   "resourceType": "Parameters",
 *   "parameter": [
 *     {"name": "namespace", "valueString": "domain-name"},
 *     {"name": "originalValue", "valueString": "patient-123"},
 *     {"name": "pseudonymValue", "valueString": "generated-pseudonym"}
 *   ]
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record VfpsParameterResponse(String resourceType, List<Parameter> parameter) {

  public String getPseudonymValue() {
    return parameter.stream()
        .filter(p -> "pseudonymValue".equals(p.name()))
        .findFirst()
        .map(Parameter::valueString)
        .orElseThrow(() -> new IllegalStateException("No pseudonymValue in Vfps response"));
  }

  public String getOriginalValue() {
    return parameter.stream()
        .filter(p -> "originalValue".equals(p.name()))
        .findFirst()
        .map(Parameter::valueString)
        .orElseThrow(() -> new IllegalStateException("No originalValue in Vfps response"));
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Parameter(String name, String valueString, ValueIdentifier valueIdentifier) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ValueIdentifier(String system, String value) {}
}
