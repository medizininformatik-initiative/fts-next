package care.smith.fts.util.error.fhir;

import static org.assertj.core.api.Assertions.assertThat;

import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import reactor.test.StepVerifier;

class FhirErrorResponseUtilTest {

  @Test
  void fromFhirException() {
    StepVerifier.create(
            FhirErrorResponseUtil.fromFhirException(
                new FhirException(HttpStatus.INTERNAL_SERVER_ERROR, "test")))
        .assertNext(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
  }

  @Test
  void internalServerErrorViaException() {
    StepVerifier.create(FhirErrorResponseUtil.internalServerError(new RuntimeException()))
        .assertNext(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
  }

  @Test
  void internalServerError() {
    StepVerifier.create(FhirErrorResponseUtil.internalServerError(new OperationOutcome()))
        .assertNext(r -> assertThat(r.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR));
  }
}
