package care.smith.fts.util.error.fhir;

import lombok.Getter;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.http.HttpStatusCode;

@Getter
public class FhirException extends Exception {
  HttpStatusCode statusCode;
  OperationOutcome operationOutcome;

  public FhirException(HttpStatusCode statusCode, String message) {
    super(message);
    this.statusCode = statusCode;
    operationOutcome = new OperationOutcome();
    operationOutcome
        .addIssue()
        .setSeverity(OperationOutcome.IssueSeverity.ERROR)
        .setDiagnostics(message);
  }

  public FhirException(HttpStatusCode statusCode, OperationOutcome operationOutcome) {
    super(operationOutcome.getIssueFirstRep().getDiagnostics());
    this.statusCode = statusCode;
    this.operationOutcome = operationOutcome;
  }
}
