package care.smith.fts.rda;

import static care.smith.fts.api.rda.BundleSender.*;
import static reactor.core.publisher.Mono.just;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.rda.BundleSender;
import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

class DefaultTransferProcessDefinitionRunnerTest {

  private DefaultTransferProcessRunner runner;

  @BeforeEach
  void setUp() {
    runner = new DefaultTransferProcessRunner();
  }

  @Test
  void runMockTestSuccessfully() {
    BundleSender.Result result = new Result();
    TransferProcessDefinition process =
        new TransferProcessDefinition("test", (b) -> Mono.just(new Bundle()), (b) -> just(result));

    create(runner.run(process, Mono.just(new TransportBundle(new Bundle(), Set.of()))))
        .expectNext(new TransferProcessRunner.Result(0, 0))
        .verifyComplete();
  }
}
