package care.smith.fts.cda.rest;

import static java.util.List.of;
import static reactor.core.publisher.Mono.just;
import static reactor.test.StepVerifier.create;

import care.smith.fts.cda.TransferProcess;
import care.smith.fts.cda.TransferProcessRunner.SummaryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransferProcessControllerTest {

  private static final SummaryResult PATIENT_SUMMARY_RESULT = new SummaryResult(0, 0);
  private TransferProcessController api;

  @BeforeEach
  void setUp() {
    api =
        new TransferProcessController(
            r -> just(new SummaryResult(0, 0)), of(mockTransferProcess()));
  }

  @Test
  void startExistingProjectSucceeds() {
    create(api.start("example")).expectNext(PATIENT_SUMMARY_RESULT).verifyComplete();
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
