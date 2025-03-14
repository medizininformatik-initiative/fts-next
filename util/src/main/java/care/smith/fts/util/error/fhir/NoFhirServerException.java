package care.smith.fts.util.error.fhir;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import org.springframework.http.HttpStatus;

public class NoFhirServerException extends FhirException {
  private static final HttpStatus status = SERVICE_UNAVAILABLE;

  public NoFhirServerException(String message, Throwable cause) {
    super(status, message, cause);
  }
}
