package care.smith.fts.cda.rest.it;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

public class CohortSelectorIT extends TransferProcessControllerIT {

  @Test
  void tcaDown() {
    mockCohortSelector.isDown();
    startProcessExpectError(Duration.ofSeconds(1));
  }

  @Test
  void tcaTimeoutConsentedPatientsRequest() {
    mockCohortSelector.timeout();
    startProcessExpectError(Duration.ofSeconds(10));
  }

  @Test
  void tcaSendsWrongContentType() throws IOException {
    mockCohortSelector.wrongContentType();
    startProcessExpectError(Duration.ofMillis(200));
  }

  @Test
  void unknownDomain() throws JsonProcessingException {
    mockCohortSelector.unknownDomain(om);
    startProcessExpectError(Duration.ofMillis(200));
  }
}
