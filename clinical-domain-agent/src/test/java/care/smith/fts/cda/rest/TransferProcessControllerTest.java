package care.smith.fts.cda.rest;

import static java.util.List.of;
import static reactor.core.publisher.Flux.just;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.cda.DefaultTransferProcessRunner.Result;
import care.smith.fts.cda.TransferProcess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransferProcessControllerTest {

  private ConsentedPatient patient;
  private TransferProcessController api;

  @BeforeEach
  void setUp() {
    patient = new ConsentedPatient("patient-102931");
    api = new TransferProcessController(r -> just(new Result(patient)), of(mockTransferProcess()));
  }

  @Test
  void startExistingProjectSucceeds() {
    create(api.start("example")).expectNext(new Result(patient)).verifyComplete();
  }

  @Test
  void startNonExistingProjectErrors() {
    create(api.start("non-existent")).expectError(IllegalStateException.class).verify();
  }

  private static TransferProcess mockTransferProcess() {
    return new TransferProcess(
        "example",
        () -> null,
        consentedPatient -> null,
        (patientBundle) -> null,
        (transportBundle) -> null);
  }
}
