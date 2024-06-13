package care.smith.fts.cda.rest;

import static java.util.List.of;
import static reactor.core.publisher.Flux.just;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.cda.R4TransferProcessRunner.Result;
import care.smith.fts.cda.TransferProcess;
import org.hl7.fhir.r4.model.Bundle;
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

  private static TransferProcess<Bundle> mockTransferProcess() {
    return new TransferProcess<>(
        "example",
        () -> null,
        consentedPatient -> null,
        (patientBundle, patient1) -> null,
        (bundle, patient1) -> null);
  }
}
