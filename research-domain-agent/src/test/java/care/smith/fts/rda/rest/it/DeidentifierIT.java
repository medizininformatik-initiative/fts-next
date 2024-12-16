package care.smith.fts.rda.rest.it;


import care.smith.fts.test.FhirGenerators;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClientResponseException.InternalServerError;

@Slf4j
public class DeidentifierIT extends TransferProcessControllerIT {
  Bundle transportBundle;

  @BeforeEach
  void setUp() throws IOException {
    transportBundle = FhirGenerators.transportBundle().generateResource();
  }

  @Test
  void tcaDown() {
    mockDeidentifier.isDown();
    startProcess(Duration.ofSeconds(3), transportBundle)
        .expectError(InternalServerError.class)
        .verify();
  }

  @Test
  void tcaTimeout() {
    mockDeidentifier.hasTimeout();
    startProcess(Duration.ofMinutes(1), transportBundle)
        .expectError(InternalServerError.class)
        .verify();
  }

  @Test
  void tcaReturnsWrongContentType() {
    mockDeidentifier.returnsWrongContentType();
    startProcess(Duration.ofSeconds(3), transportBundle)
        .expectError(InternalServerError.class)
        .verify();
  }

  @Test
  void tcaFirstRequestFails() throws IOException {
    mockDeidentifier.success(List.of(500));
    mockBundleSender.success();

    log.info("Start process with transport bundle of size {}", transportBundle.getEntry().size());

    startProcess(Duration.ofSeconds(3), transportBundle)
        .assertNext(r -> completeWithResources(r, 366, 1))
        .verifyComplete();
  }
}
