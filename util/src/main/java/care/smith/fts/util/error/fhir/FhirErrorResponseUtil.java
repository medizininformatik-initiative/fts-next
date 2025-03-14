package care.smith.fts.util.error.fhir;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

public interface FhirErrorResponseUtil {

  @SuppressWarnings("unchecked")
  static <T> Mono<ResponseEntity<T>> fromFhirException(FhirException e) {
    return Mono.just(
        (ResponseEntity<T>) ResponseEntity.status(e.getStatusCode()).body(e.getMessage()));
  }

  @SuppressWarnings("unchecked")
  private static <T> Mono<ResponseEntity<T>> onError(
      OperationOutcome outcome, HttpStatus httpStatus) {
    return Mono.just((ResponseEntity<T>) ResponseEntity.status(httpStatus).body(outcome));
  }

  static <T> Mono<ResponseEntity<T>> internalServerError(OperationOutcome outcome) {
    return onError(outcome, HttpStatus.INTERNAL_SERVER_ERROR);
  }

  @SuppressWarnings("unchecked")
  private static <T> Mono<ResponseEntity<T>> onError(Throwable e, HttpStatus httpStatus) {
    var outcome = operationOutcomeWithIssue(e);
    return Mono.just((ResponseEntity<T>) ResponseEntity.status(httpStatus).body(outcome));
  }

  static OperationOutcome operationOutcomeWithIssue(Throwable e) {
    var outcome = new OperationOutcome();
    outcome
        .addIssue()
        .setSeverity(OperationOutcome.IssueSeverity.ERROR)
        .setDiagnostics(e.getMessage());
    return outcome;
  }

  static <T> Mono<ResponseEntity<T>> internalServerError(Throwable e) {
    return onError(e, HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
