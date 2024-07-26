package care.smith.fts.rda.rest.it;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.rda.TransferProcessRunner.Phase;
import care.smith.fts.test.FhirGenerators;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class DeidentifierIT extends TransferProcessControllerIT {

  @Test
  void tcaDown() {
    mockDeidentifier.isDown();
    startProcess(Duration.ofSeconds(1))
        .assertNext(r -> assertThat(r.phase()).isEqualTo(Phase.ERROR))
        .verifyComplete();
  }

  @Test
  void tcaTimeout() {
    mockDeidentifier.hasTimeout();
    startProcess(Duration.ofSeconds(10))
        .assertNext(r -> assertThat(r.phase()).isEqualTo(Phase.ERROR))
        .verifyComplete();
  }

  @Test
  void tcaReturnsWrongContentType() {
    mockDeidentifier.returnsWrongContentType();
    startProcess(Duration.ofMillis(200))
        .assertNext(r -> assertThat(r.phase()).isEqualTo(Phase.ERROR))
        .verifyComplete();
  }

  @Test
  void tcaFirstRequestFails() throws IOException {
    mockDeidentifier.success(List.of(500));
    mockBundleSender.success();

    var transportBundle = FhirGenerators.transportBundle().generateResource();

    log.info("Start process with transport bundle of size {}", transportBundle.getEntry().size());

    startProcess(Duration.ofSeconds(3), transportBundle)
        .assertNext(r -> completeWithResources(r, 366, 1))
        .verifyComplete();
  }
}
