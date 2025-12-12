package care.smith.fts.tca.rest;

import static java.util.Objects.requireNonNull;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.StringType;

/**
 * Vfps-compatible pseudonymization response to be converted to FHIR Parameters.
 *
 * <p>This DTO represents the internal representation of a Vfps $create-pseudonym response before
 * serialization to FHIR Parameters. The response contains pseudonym entries with:
 *
 * <ul>
 *   <li>namespace - The original namespace from the request
 *   <li>originalValue - The original value that was pseudonymized
 *   <li>pseudonymValue - The generated pseudonym (transport ID for CDA, real sID for RDA)
 * </ul>
 *
 * @param pseudonyms List of pseudonym entries
 */
public record VfpsPseudonymizeResponse(@NotNull List<PseudonymEntry> pseudonyms) {

  public VfpsPseudonymizeResponse {
    requireNonNull(pseudonyms, "pseudonyms is required");
    pseudonyms = List.copyOf(pseudonyms);
  }

  /**
   * A single pseudonym mapping entry.
   *
   * @param namespace The domain/namespace
   * @param original The original value
   * @param pseudonym The generated pseudonym (tID or sID depending on endpoint)
   */
  public record PseudonymEntry(String namespace, String original, String pseudonym) {}

  /** Converts this response to FHIR Parameters format. */
  public Parameters toFhirParameters() {
    var fhirParams = new Parameters();

    for (var entry : pseudonyms) {
      if (pseudonyms.size() == 1) {
        fhirParams.addParameter().setName("namespace").setValue(new StringType(entry.namespace()));
        fhirParams
            .addParameter()
            .setName("originalValue")
            .setValue(new StringType(entry.original()));
        fhirParams
            .addParameter()
            .setName("pseudonymValue")
            .setValue(new StringType(entry.pseudonym()));
      } else {
        var pseudonymParam = new ParametersParameterComponent();
        pseudonymParam.setName("pseudonym");
        pseudonymParam.addPart().setName("namespace").setValue(new StringType(entry.namespace()));
        pseudonymParam
            .addPart()
            .setName("originalValue")
            .setValue(new StringType(entry.original()));
        pseudonymParam
            .addPart()
            .setName("pseudonymValue")
            .setValue(new StringType(entry.pseudonym()));
        fhirParams.addParameter(pseudonymParam);
      }
    }
    return fhirParams;
  }
}
