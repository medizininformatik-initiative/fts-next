package care.smith.fts.rda.rest.it;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.rda.TransferProcessRunner.Phase;
import care.smith.fts.test.FhirGenerators;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@Slf4j
public class BundleSenderIT extends TransferProcessControllerIT {
  Bundle transportBundle;

  @BeforeEach
  void setUp() throws IOException {
    transportBundle = FhirGenerators.transportBundle().generateResource();
  }

  @Test
  void hdsDown() {
    mockDeidentifier.success();
    mockBundleSender.isDown();

    startProcess(Duration.ofSeconds(1), transportBundle)
        .assertNext(r -> assertThat(r.phase()).isEqualTo(Phase.ERROR))
        .verifyComplete();
  }

  @Test
  void hdsTimeout() {
    mockDeidentifier.success();
    mockBundleSender.hasTimeout();

    startProcess(Duration.ofSeconds(12), transportBundle)
        .assertNext(r -> assertThat(r.phase()).isEqualTo(Phase.ERROR))
        .verifyComplete();
  }

  @Test
  void hdsFirstRequestFails() {
    mockDeidentifier.success();
    mockBundleSender.success(List.of(500));

    startProcess(Duration.ofSeconds(3), transportBundle)
        .assertNext(r -> completeWithResources(r, 366, 1))
        .verifyComplete();
  }
}
