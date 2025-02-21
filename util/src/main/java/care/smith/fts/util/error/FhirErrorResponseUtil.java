package care.smith.fts.util.error;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

public interface FhirErrorResponseUtil {

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

  static <T> Mono<ResponseEntity<T>> badRequest(Throwable e) {
    return onError(e, HttpStatus.BAD_REQUEST);
  }

  static <T> Mono<ResponseEntity<T>> internalServerError(Throwable e) {
    return onError(e, HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
