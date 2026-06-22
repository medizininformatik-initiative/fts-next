package care.smith.fts.rda;

import static care.smith.fts.api.rda.BundleSender.*;
import static org.assertj.core.api.Assertions.assertThat;
import static reactor.core.publisher.Mono.just;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.api.rda.Deidentificator;
import care.smith.fts.rda.TransferProcessRunner.Phase;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import java.time.Duration;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class DefaultTransferProcessRunnerTest {

  private DefaultTransferProcessRunner runner;

  @BeforeEach
  void setUp() {
    runner = new DefaultTransferProcessRunner(new ObjectMapper(), bulkheadRegistry(10), config());
  }

  private static BulkheadRegistry bulkheadRegistry(int maxConcurrent) {
    return BulkheadRegistry.of(
        BulkheadConfig.custom()
            .maxConcurrentCalls(maxConcurrent)
            .maxWaitDuration(Duration.ZERO)
            .build());
  }

  private static TransferProcessRunnerConfig config() {
    return new TransferProcessRunnerConfig(10, 5, Duration.ofHours(1));
  }

  private static TransferProcessRunner.Status awaitCompletion(
      TransferProcessRunner runner, String processId) {
    var result =
        Flux.interval(Duration.ofMillis(50))
            .flatMap(i -> runner.status(processId))
            .takeUntil(s -> s.phase() == Phase.COMPLETED || s.phase() == Phase.ERROR)
            .take(Duration.ofSeconds(10))
            .blockLast();
    assertThat(result).as("process %s did not complete within timeout", processId).isNotNull();
    return result;
  }

  @Test
  void runMockTestSuccessfully() {
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
    var status = awaitCompletion(runner, processId);
    assertThat(status.phase()).isEqualTo(Phase.COMPLETED);
    assertThat(status.receivedResources()).isEqualTo(1);
    assertThat(status.sentResources()).isEqualTo(1);
  }

  @Test
  void rejectsWhenSaturated() {
    var registry = bulkheadRegistry(1);
    var saturatedRunner = new DefaultTransferProcessRunner(new ObjectMapper(), registry, config());
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
  void releasesPermitOnCompletion() {
    var registry = bulkheadRegistry(1);
    var limitedRunner = new DefaultTransferProcessRunner(new ObjectMapper(), registry, config());
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
    var processId = ((TransferProcessRunner.StartResult.Accepted) first).processId();

    awaitCompletion(limitedRunner, processId);

    TransferProcessRunner.StartResult afterCompletion =
        limitedRunner.start(
            process,
            Mono.just(
                new TransportBundle(
                    new Bundle().addEntry(new Bundle().getEntryFirstRep()), "transferId-2")));
    assertThat(afterCompletion).isInstanceOf(TransferProcessRunner.StartResult.Accepted.class);
  }

  @Test
  void sharedDestinationSharesBudgetAcrossProjects() {
    var registry = bulkheadRegistry(1);
    var runner = new DefaultTransferProcessRunner(new ObjectMapper(), registry, config());
    var slowSender =
        new FixedDestinationSender(
            "same-dest", Mono.delay(Duration.ofSeconds(10)).then(Mono.just(new Result())));
    Deidentificator deidentificator =
        (b) -> Mono.just(new Bundle().addEntry(new Bundle().getEntryFirstRep()));

    var processA =
        new TransferProcessDefinition(
            "project-a", new TransferProcessConfig(null, null), deidentificator, slowSender);
    var processB =
        new TransferProcessDefinition(
            "project-b", new TransferProcessConfig(null, null), deidentificator, slowSender);

    var first =
        runner.start(
            processA,
            Mono.just(
                new TransportBundle(new Bundle().addEntry(new Bundle().getEntryFirstRep()), "t1")));
    assertThat(first).isInstanceOf(TransferProcessRunner.StartResult.Accepted.class);

    var second =
        runner.start(
            processB,
            Mono.just(
                new TransportBundle(new Bundle().addEntry(new Bundle().getEntryFirstRep()), "t2")));
    assertThat(second).isInstanceOf(TransferProcessRunner.StartResult.Rejected.class);
  }

  @Test
  void differentDestinationsGetIndependentBudgets() {
    var registry = bulkheadRegistry(1);
    var runner = new DefaultTransferProcessRunner(new ObjectMapper(), registry, config());
    var slowSenderA =
        new FixedDestinationSender(
            "dest-a", Mono.delay(Duration.ofSeconds(10)).then(Mono.just(new Result())));
    var slowSenderB =
        new FixedDestinationSender(
            "dest-b", Mono.delay(Duration.ofSeconds(10)).then(Mono.just(new Result())));
    Deidentificator deidentificator =
        (b) -> Mono.just(new Bundle().addEntry(new Bundle().getEntryFirstRep()));

    var processA =
        new TransferProcessDefinition(
            "project-a", new TransferProcessConfig(null, null), deidentificator, slowSenderA);
    var processB =
        new TransferProcessDefinition(
            "project-b", new TransferProcessConfig(null, null), deidentificator, slowSenderB);

    var first =
        runner.start(
            processA,
            Mono.just(
                new TransportBundle(new Bundle().addEntry(new Bundle().getEntryFirstRep()), "t1")));
    assertThat(first).isInstanceOf(TransferProcessRunner.StartResult.Accepted.class);

    var second =
        runner.start(
            processB,
            Mono.just(
                new TransportBundle(new Bundle().addEntry(new Bundle().getEntryFirstRep()), "t2")));
    assertThat(second).isInstanceOf(TransferProcessRunner.StartResult.Accepted.class);
  }

  @Test
  void emptyDataCompletesPhase() {
    TransferProcessDefinition process =
        new TransferProcessDefinition(
            "test",
            new TransferProcessConfig(null, null),
            (b) -> Mono.just(new Bundle().addEntry(new Bundle().getEntryFirstRep())),
            (b) -> just(new Result()));

    TransferProcessRunner.StartResult startResult = runner.start(process, Mono.empty());
    assertThat(startResult).isInstanceOf(TransferProcessRunner.StartResult.Accepted.class);
    String processId = ((TransferProcessRunner.StartResult.Accepted) startResult).processId();
    assertThat(awaitCompletion(runner, processId).phase()).isEqualTo(Phase.COMPLETED);
  }

  @Test
  void instanceEvictedAfterTtl() {
    var ttlRunner =
        new DefaultTransferProcessRunner(
            new ObjectMapper(),
            bulkheadRegistry(10),
            new TransferProcessRunnerConfig(10, 5, Duration.ofMillis(1)));
    TransferProcessDefinition process =
        new TransferProcessDefinition(
            "test",
            new TransferProcessConfig(null, null),
            (b) -> Mono.just(new Bundle().addEntry(new Bundle().getEntryFirstRep())),
            (b) -> just(new Result()));

    var first =
        ttlRunner.start(
            process,
            Mono.just(
                new TransportBundle(
                    new Bundle().addEntry(new Bundle().getEntryFirstRep()), "transferId-1")));
    assertThat(first).isInstanceOf(TransferProcessRunner.StartResult.Accepted.class);
    var processId = ((TransferProcessRunner.StartResult.Accepted) first).processId();

    awaitCompletion(ttlRunner, processId);

    // eviction is start-triggered by design; trigger with a second process
    ttlRunner.start(
        process,
        Mono.just(
            new TransportBundle(
                new Bundle().addEntry(new Bundle().getEntryFirstRep()), "transferId-2")));

    create(ttlRunner.status(processId)).expectError(IllegalArgumentException.class).verify();
  }

  @Test
  void instanceNotEvictedBeforeTtl() {
    TransferProcessDefinition process =
        new TransferProcessDefinition(
            "test",
            new TransferProcessConfig(null, null),
            (b) -> Mono.just(new Bundle().addEntry(new Bundle().getEntryFirstRep())),
            (b) -> just(new Result()));

    var first =
        runner.start(
            process,
            Mono.just(
                new TransportBundle(
                    new Bundle().addEntry(new Bundle().getEntryFirstRep()), "transferId-1")));
    assertThat(first).isInstanceOf(TransferProcessRunner.StartResult.Accepted.class);
    var processId = ((TransferProcessRunner.StartResult.Accepted) first).processId();

    awaitCompletion(runner, processId);

    // trigger potential eviction by starting another process
    runner.start(
        process,
        Mono.just(
            new TransportBundle(
                new Bundle().addEntry(new Bundle().getEntryFirstRep()), "transferId-2")));

    // 1-hour TTL not expired; instance should still be present
    create(runner.status(processId))
        .assertNext(r -> assertThat(r.phase()).isEqualTo(Phase.COMPLETED))
        .verifyComplete();
  }

  private static class FixedDestinationSender implements BundleSender {
    private final String id;
    private final Mono<Result> response;

    FixedDestinationSender(String id, Mono<Result> response) {
      this.id = id;
      this.response = response;
    }

    @Override
    public Mono<Result> send(Bundle bundle) {
      return response;
    }

    @Override
    public String destinationId() {
      return id;
    }
  }
}
