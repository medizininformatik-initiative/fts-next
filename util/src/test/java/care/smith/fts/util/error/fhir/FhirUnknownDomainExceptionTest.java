package care.smith.fts.util.error.fhir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.Test;

class FhirUnknownDomainExceptionTest {
  @Test
  void fhirUnknownDomainException() {
    var e = new FhirUnknownDomainException("message");
    assertThat(e.getStatusCode()).isEqualTo(NOT_FOUND);
    assertThat(e).hasMessage("message");
    assertThat(e.getOperationOutcome().getIssueFirstRep().getDiagnostics()).isEqualTo("message");
  }

  @Test
  void fhirUnknownDomainExceptionFromOperationOutcome() {
    var outcome = new OperationOutcome();
    outcome.addIssue().setDiagnostics("message");
    var e = new FhirUnknownDomainException(outcome);
    assertThat(e.getStatusCode()).isEqualTo(NOT_FOUND);
    assertThat(e).hasMessage("message");
    assertThat(e.getOperationOutcome().getIssueFirstRep().getDiagnostics()).isEqualTo("message");
  }
}
