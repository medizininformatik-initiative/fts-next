package care.smith.fts.util.error.fhir;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

import org.springframework.http.HttpStatus;

public class FhirConnectException extends FhirException {
  private static final HttpStatus status = BAD_GATEWAY;

  public FhirConnectException(String message) {
    super(status, message);
  }
}
