package care.smith.fts.util.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.test.StepVerifier;

class ErrorResponseUtilTest {

  @Test
  void badRequest() {
    StepVerifier.create(ErrorResponseUtil.badRequest(new RuntimeException()))
        .assertNext(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void notFound() {
    StepVerifier.create(ErrorResponseUtil.notFound(new RuntimeException()))
        .assertNext(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
  }

  @Test
  void internalServerError() {
    StepVerifier.create(ErrorResponseUtil.internalServerError(new RuntimeException()))
        .assertNext(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
  }
}
