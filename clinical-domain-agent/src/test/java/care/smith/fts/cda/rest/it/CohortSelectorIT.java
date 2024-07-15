package care.smith.fts.cda.rest.it;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.cda.TransferProcessRunner.Phase;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.Test;

public class CohortSelectorIT extends TransferProcessControllerIT {

  @Test
  void tcaDown() {
    mockCohortSelector.isDown();
    startProcess(
        Duration.ofSeconds(1),
        r -> {
          assertThat(r.phase()).isEqualTo(Phase.ERROR);
        });
  }

  @Test
  void tcaTimeoutConsentedPatientsRequest() {
    mockCohortSelector.timeout();
    startProcess(
        Duration.ofSeconds(10),
        r -> {
          assertThat(r.phase()).isEqualTo(Phase.ERROR);
        });
  }

  @Test
  void tcaSendsWrongContentType() throws IOException {
    mockCohortSelector.wrongContentType();
    startProcess(
        Duration.ofMillis(200),
        r -> {
          assertThat(r.phase()).isEqualTo(Phase.ERROR);
        });
  }

  @Test
  void unknownDomain() throws JsonProcessingException {
    mockCohortSelector.unknownDomain(om);
    startProcess(
        Duration.ofMillis(200),
        r -> {
          assertThat(r.phase()).isEqualTo(Phase.ERROR);
        });
  }
}
