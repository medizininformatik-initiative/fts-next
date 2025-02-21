package care.smith.fts.util.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.test.StepVerifier;

class FhirErrorResponseUtilTest {

  @Test
  void badRequest() {
    StepVerifier.create(FhirErrorResponseUtil.badRequest(new RuntimeException()))
        .assertNext(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
  }

  @Test
  void internalServerError() {
    StepVerifier.create(FhirErrorResponseUtil.internalServerError(new RuntimeException()))
        .assertNext(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
  }
}
