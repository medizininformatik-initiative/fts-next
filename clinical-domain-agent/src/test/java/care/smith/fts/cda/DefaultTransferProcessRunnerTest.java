package care.smith.fts.cda;

import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static reactor.core.publisher.Flux.fromIterable;
import static reactor.core.publisher.Mono.just;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.*;
import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.cda.BundleSender;
import java.util.List;
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
  void runMockTestSuccessfully() throws InterruptedException {
    BundleSender.Result result = new BundleSender.Result(1);
    TransferProcessDefinition process =
        new TransferProcessDefinition(
            "test",
            pids -> fromIterable(List.of(PATIENT)),
            p -> fromIterable(List.of(new Bundle())),
            b -> fromIterable(List.of(new TransportBundle(new Bundle(), "tIDMapName"))),
            b -> just(result));

    String processId = runner.start(process, List.of());
    sleep(500L);
    create(runner.status(processId))
        .assertNext(
            r -> {
              assertThat(r.bundlesSentCount()).isEqualTo(1);
              assertThat(r.patientsSkippedCount()).isEqualTo(0);
            })
        .verifyComplete();
  }
}
