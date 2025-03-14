package care.smith.fts.util.error.fhir;

import lombok.Getter;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.http.HttpStatusCode;

@Getter
public class FhirException extends Exception {
  private final HttpStatusCode statusCode;

  public FhirException(HttpStatusCode statusCode, String message, Throwable cause) {
    super(message, cause);
    this.statusCode = statusCode;
  }

  public FhirException(HttpStatusCode statusCode, String message) {
    super(message);
    this.statusCode = statusCode;
  }

  public OperationOutcome getOperationOutcome() {
    var operationOutcome = new OperationOutcome();
    operationOutcome
        .addIssue()
        .setSeverity(OperationOutcome.IssueSeverity.ERROR)
        .setDiagnostics(getMessage());
    return operationOutcome;
  }
}
