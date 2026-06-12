package care.smith.fts.rda;

import static care.smith.fts.api.rda.BundleSender.*;
import static java.lang.Thread.sleep;
import static org.assertj.core.api.Assertions.assertThat;
import static reactor.core.publisher.Mono.just;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.rda.TransferProcessRunner.Phase;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import java.time.Duration;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class DefaultTransferProcessRunnerTest {

  private DefaultTransferProcessRunner runner;

  @BeforeEach
  void setUp() {
    runner = new DefaultTransferProcessRunner(new ObjectMapper(), bulkheadRegistry(10));
  }

  private static BulkheadRegistry bulkheadRegistry(int maxConcurrent) {
    return BulkheadRegistry.of(
        BulkheadConfig.custom()
            .maxConcurrentCalls(maxConcurrent)
            .maxWaitDuration(Duration.ZERO)
            .build());
  }

  @Test
  void runMockTestSuccessfully() throws InterruptedException {
    BundleSender.Result result = new Result();
    TransferProcessDefinition process =
        new TransferProcessDefinition(
            "test",
            new TransferProcessConfig(null, null),
            (b) -> Mono.just(new Bundle().addEntry(new Bundle().getEntryFirstRep())),
            (b) -> just(result));

    TransferProcessRunner.StartResult startResult =
        runner.start(
            process,
            Mono.just(
                new TransportBundle(
                    new Bundle().addEntry(new Bundle().getEntryFirstRep()), "transferId")));
    assertThat(startResult).isInstanceOf(TransferProcessRunner.StartResult.Accepted.class);
    String processId = ((TransferProcessRunner.StartResult.Accepted) startResult).processId();
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

  @Test
  void rejectsWhenSaturated() {
    var registry = bulkheadRegistry(1);
    var saturatedRunner = new DefaultTransferProcessRunner(new ObjectMapper(), registry);
    TransferProcessDefinition process =
        new TransferProcessDefinition(
            "test",
            new TransferProcessConfig(null, null),
            (b) -> Mono.just(new Bundle().addEntry(new Bundle().getEntryFirstRep())),
            (b) -> Mono.delay(Duration.ofSeconds(10)).then(Mono.just(new Result())));

    TransferProcessRunner.StartResult first =
        saturatedRunner.start(
            process,
            Mono.just(
                new TransportBundle(
                    new Bundle().addEntry(new Bundle().getEntryFirstRep()), "transferId-1")));
    assertThat(first).isInstanceOf(TransferProcessRunner.StartResult.Accepted.class);

    TransferProcessRunner.StartResult second =
        saturatedRunner.start(
            process,
            Mono.just(
                new TransportBundle(
                    new Bundle().addEntry(new Bundle().getEntryFirstRep()), "transferId-2")));
    assertThat(second).isInstanceOf(TransferProcessRunner.StartResult.Rejected.class);
  }

  @Test
  void releasesPermitOnCompletion() throws InterruptedException {
    var registry = bulkheadRegistry(1);
    var limitedRunner = new DefaultTransferProcessRunner(new ObjectMapper(), registry);
    TransferProcessDefinition process =
        new TransferProcessDefinition(
            "test",
            new TransferProcessConfig(null, null),
            (b) -> Mono.just(new Bundle().addEntry(new Bundle().getEntryFirstRep())),
            (b) -> just(new Result()));

    TransferProcessRunner.StartResult first =
        limitedRunner.start(
            process,
            Mono.just(
                new TransportBundle(
                    new Bundle().addEntry(new Bundle().getEntryFirstRep()), "transferId-1")));
    assertThat(first).isInstanceOf(TransferProcessRunner.StartResult.Accepted.class);

    sleep(500L);

    TransferProcessRunner.StartResult afterCompletion =
        limitedRunner.start(
            process,
            Mono.just(
                new TransportBundle(
                    new Bundle().addEntry(new Bundle().getEntryFirstRep()), "transferId-2")));
    assertThat(afterCompletion).isInstanceOf(TransferProcessRunner.StartResult.Accepted.class);
  }
}
