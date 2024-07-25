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
public class BundleSenderIT extends TransferProcessControllerIT {
  @Test
  void hdsDown() {
    mockDeidentifier.success();
    mockBundleSender.isDown();
    startProcessAndExpectError(Duration.ofSeconds(1));
  }

  @Test
  void hdsTimeout() {
    mockDeidentifier.success();
    mockBundleSender.hasTimeout();
    startProcessAndExpectError(Duration.ofSeconds(12));
  }

  @Test
  void hdsFirstRequestFails() throws IOException {
    mockDeidentifier.success();
    mockBundleSender.success(List.of(500));

    var transportBundle = FhirGenerators.transportBundle().generateResource();

    log.info("Start process with transport bundle of size {}", transportBundle.getEntry().size());

    startProcess(
        transportBundle,
        Duration.ofSeconds(3),
        r -> {
          assertThat(r.phase()).isEqualTo(Phase.COMPLETED);
          assertThat(r.receivedResources()).isEqualTo(366);
          assertThat(r.sentResources()).isEqualTo(1);
        });
  }
}
