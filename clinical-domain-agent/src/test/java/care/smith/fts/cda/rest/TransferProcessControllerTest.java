package care.smith.fts.cda.rest;

import static java.util.List.of;
import static reactor.core.publisher.Flux.just;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.cda.TransferProcess;
import care.smith.fts.cda.TransferProcessRunner.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransferProcessControllerTest {

  private static final ConsentedPatient PATIENT = new ConsentedPatient("patient-102931");
  private static final Result RESULT = new Result(PATIENT, 0, 0, 0, 0);
  private ConsentedPatient patient;
  private TransferProcessController api;

  @BeforeEach
  void setUp() {
    api = new TransferProcessController(r -> just(RESULT), of(mockTransferProcess()));
  }

  @Test
  void startExistingProjectSucceeds() {
    create(api.start("example")).expectNext(RESULT).verifyComplete();
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
