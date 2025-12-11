package care.smith.fts.tca.deidentification;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Record to deserialize Entici response from $pseudonymize operation.
 *
 * <p>Response format:
 *
 * <pre>
 * {
 *   "resourceType": "Parameters",
 *   "parameter": [
 *     {
 *       "name": "pseudonym",
 *       "valueIdentifier": {
 *         "system": "domain-namespace-url",
 *         "value": "generated-pseudonym"
 *       }
 *     }
 *   ]
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EnticiParameterResponse(String resourceType, List<Parameter> parameter) {

  public String getPseudonymValue() {
    return parameter.stream()
        .filter(p -> "pseudonym".equals(p.name()))
        .findFirst()
        .map(p -> p.valueIdentifier().value())
        .orElseThrow(() -> new IllegalStateException("No pseudonym in Entici response"));
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Parameter(String name, String valueString, ValueIdentifier valueIdentifier) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ValueIdentifier(String system, String value) {}
}
