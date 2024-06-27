package care.smith.fts.rda.rest;

import static java.util.List.of;
import static reactor.core.publisher.Mono.just;
import static reactor.test.StepVerifier.create;

import care.smith.fts.rda.TransferProcess;
import care.smith.fts.rda.TransferProcessRunner.Result;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class TransferProcessControllerTest {

  private TransferProcessController api;

  @BeforeEach
  void setUp() {
    api =
        new TransferProcessController((r, p) -> just(new Result(0, 0)), of(mockTransferProcess()));
  }

  @Test
  void startExistingProjectSucceeds() {
    create(api.start("example", Mono.just(new Bundle())))
        .expectNext(new Result(0, 0))
        .verifyComplete();
  }

  @Test
  void startNonExistingProjectErrors() {
    create(api.start("non-existent", Mono.just(new Bundle())))
        .expectError(IllegalStateException.class)
        .verify();
  }

  private static TransferProcess mockTransferProcess() {
    return new TransferProcess("example", (transportBundle) -> null, (patientBundle) -> null);
  }
}
