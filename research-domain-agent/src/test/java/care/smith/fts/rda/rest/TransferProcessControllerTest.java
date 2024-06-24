package care.smith.fts.rda.rest;

import static java.util.List.of;
import static reactor.core.publisher.Mono.just;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.rda.BundleSender.Result;
import care.smith.fts.rda.TransferProcess;
import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class TransferProcessControllerTest {

  private TransferProcessController api;

  @BeforeEach
  void setUp() {
    api = new TransferProcessController((r, p) -> just(new Result(1)), of(mockTransferProcess()));
  }

  @Test
  void startExistingProjectSucceeds() {
    create(api.start("example", Flux.just(new TransportBundle(new Bundle(), Set.of()))))
        .expectNext(new Result(1))
        .verifyComplete();
  }

  @Test
  void startNonExistingProjectErrors() {
    create(api.start("non-existent", Flux.just()))
        .expectError(IllegalStateException.class)
        .verify();
  }

  private static TransferProcess mockTransferProcess() {
    return new TransferProcess("example", (transportBundle) -> null, (patientBundle) -> null);
  }
}
