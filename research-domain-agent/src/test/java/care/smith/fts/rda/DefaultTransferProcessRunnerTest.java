package care.smith.fts.rda;

import static care.smith.fts.api.rda.BundleSender.*;
import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static reactor.core.publisher.Mono.just;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.rda.TransferProcessRunner.Phase;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class DefaultTransferProcessRunnerTest {

  private DefaultTransferProcessRunner runner;

  @BeforeEach
  void setUp() {
    runner = new DefaultTransferProcessRunner();
  }

  @Test
  void runMockTestSuccessfully() throws InterruptedException {
    BundleSender.Result result = new Result();
    TransferProcessDefinition process =
        new TransferProcessDefinition("test", (b) -> Mono.just(new Bundle().addEntry(new Bundle().getEntryFirstRep())), (b) -> just(result));

    String processId =
        runner.start(
            process,
            Mono.just(
                new TransportBundle(
                    new Bundle().addEntry(new Bundle().getEntryFirstRep()), "tIDMapName")));
    sleep(500L);
    create(runner.status(processId))
        .assertNext(
            r -> {
              assertThat(r.phase()).isEqualTo(Phase.COMPLETED);
              assertThat(r.receivedResources()).isEqualTo(1);
              assertThat(r.sentResources()).isEqualTo(1);
            })
        .verifyComplete();
  }
}
