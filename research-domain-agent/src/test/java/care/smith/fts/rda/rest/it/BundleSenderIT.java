package care.smith.fts.rda.rest.it;

import java.time.Duration;
import org.junit.jupiter.api.Test;

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
}
