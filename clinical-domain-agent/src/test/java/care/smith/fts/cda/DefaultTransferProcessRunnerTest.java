package care.smith.fts.cda;

import static org.assertj.core.api.Assertions.assertThat;
import static reactor.core.publisher.Flux.fromIterable;
import static reactor.core.publisher.Mono.just;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.*;
import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.cda.BundleSender;
import java.util.List;
import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultTransferProcessRunnerTest {

  private static final String PATIENT_ID = "patient-150622";
  private static final ConsentedPatient PATIENT = new ConsentedPatient(PATIENT_ID);

  private DefaultTransferProcessRunner runner;

  @BeforeEach
  void setUp() {
    runner = new DefaultTransferProcessRunner();
  }

  @Test
  void runMockTestSuccessfully() {
    BundleSender.Result result = new BundleSender.Result(1);
    TransferProcess process =
        new TransferProcess(
            "test",
            () -> fromIterable(List.of(PATIENT)),
            p -> fromIterable(List.of(new Bundle())),
            (b) -> fromIterable(List.of(new TransportBundle(new Bundle(), Set.of()))),
            (b) -> just(result));

    create(runner.run(process))
        .assertNext(
            r -> {
              assertThat(r.bunesSentCount()).isEqualTo(1);
              assertThat(r.patientErrorsCount()).isEqualTo(0);
              assertThat(r.patientResults()).hasSize(1);
            })
        .verifyComplete();
  }
}
