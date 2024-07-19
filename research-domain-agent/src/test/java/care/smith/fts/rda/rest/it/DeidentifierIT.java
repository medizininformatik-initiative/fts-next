package care.smith.fts.rda.rest.it;

import java.time.Duration;
import org.junit.jupiter.api.Test;

public class DeidentifierIT extends TransferProcessControllerIT {

  @Test
  void tcaDown() {
    mockDeidentifier.isDown();
    startProcessAndExpectError(Duration.ofSeconds(1));
  }

  @Test
  void tcaTimeout() {
    mockDeidentifier.hasTimeout();
    startProcessAndExpectError(Duration.ofSeconds(10));
  }

  @Test
  void tcaReturnsWrongContentType() {
    mockDeidentifier.returnsWrongContentType();
    startProcessAndExpectError(Duration.ofMillis(200));
  }
}
