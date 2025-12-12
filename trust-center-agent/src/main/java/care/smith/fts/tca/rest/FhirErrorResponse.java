package care.smith.fts.tca.rest;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.Parameters;

/** Utility for building FHIR error responses in Vfps-compatible format. */
public interface FhirErrorResponse {

  static Parameters buildOperationOutcome(String message, IssueType issueType) {
    var outcome = new OperationOutcome();
    outcome.addIssue().setSeverity(IssueSeverity.ERROR).setCode(issueType).setDiagnostics(message);

    var params = new Parameters();
    params.addParameter().setName("outcome").setResource(outcome);
    return params;
  }
}
