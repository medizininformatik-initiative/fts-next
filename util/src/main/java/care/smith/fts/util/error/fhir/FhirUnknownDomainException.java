package care.smith.fts.util.error.fhir;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import org.springframework.http.HttpStatus;

public class FhirUnknownDomainException extends FhirException {
  private static final HttpStatus status = NOT_FOUND;

  public FhirUnknownDomainException(String message) {
    super(status, message);
  }
}
