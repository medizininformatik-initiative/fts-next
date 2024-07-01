package care.smith.fts.util.error;

import care.smith.fts.util.tca.PseudonymizeResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;

public class ErrorResponseUtil {

  private static Mono<ResponseEntity<PseudonymizeResponse>> onError(
      Throwable e, HttpStatus httpStatus) {
    return Mono.just(
        ResponseEntity.of(ProblemDetail.forStatusAndDetail(httpStatus, e.getMessage())).build());
  }

  public static Mono<ResponseEntity<PseudonymizeResponse>> badRequest(Throwable e) {
    return onError(e, HttpStatus.BAD_REQUEST);
  }
}
