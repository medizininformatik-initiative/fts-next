package care.smith.fts.cda;

import static reactor.core.publisher.Flux.fromIterable;
import static reactor.core.publisher.Mono.just;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.*;
import care.smith.fts.cda.R4TransferProcessRunner.Result;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class R4TransferProcessRunnerTest {

  private static final String PATIENT_ID = "patient-150622";
  private static final ConsentedPatient PATIENT =
      new ConsentedPatient(PATIENT_ID, new ConsentedPatient.ConsentedPolicies());

  private R4TransferProcessRunner runner;

  @BeforeEach
  void setUp() {
    runner = new R4TransferProcessRunner();
  }

  @Test
  void runMockTestSuccessfully() {
    BundleSender.Result result = new BundleSender.Result();
    TransferProcess<Bundle> process =
        new TransferProcess<>(
            "test",
            () -> fromIterable(List.of(PATIENT)),
            p -> fromIterable(List.of(new Bundle())),
            (b, p) -> fromIterable(List.of(new Bundle())),
            (b, p) -> just(result));

    create(runner.run(process)).expectNext(new Result(PATIENT)).verifyComplete();
  }
}
