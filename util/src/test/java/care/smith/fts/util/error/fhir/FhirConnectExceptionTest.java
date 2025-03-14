package care.smith.fts.util.error.fhir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

import org.junit.jupiter.api.Test;

class FhirConnectExceptionTest {

  @Test
  void fhirConnectException() {
    var e = new FhirConnectException("message");
    assertThat(e.getStatusCode()).isEqualTo(BAD_GATEWAY);
    assertThat(e).hasMessage("message");
    assertThat(e.getOperationOutcome().getIssueFirstRep().getDiagnostics()).isEqualTo("message");
  }
}
