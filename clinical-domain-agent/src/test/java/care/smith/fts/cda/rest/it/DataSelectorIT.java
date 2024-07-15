package care.smith.fts.cda.rest.it;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.cda.TransferProcessRunner.Phase;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DataSelectorIT extends TransferProcessControllerIT {
  private static final String patientId = "patientId";

  @BeforeEach
  void setUp() throws IOException {
    mockCohortSelector.successOnePatient(patientId);
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).success(patientId);
  }

  @Test
  void hdsDown() {
    mockDataSelector.whenFetchData(patientId).dropConnection();
    startProcess(
        Duration.ofSeconds(1),
        r -> {
          assertThat(r.phase()).isEqualTo(Phase.COMPLETED);
          assertThat(r.patientsSkippedCount()).isEqualTo(1);
        });
  }

  @Test
  void hdsTimeout() {
    mockDataSelector.whenFetchData(patientId).timeout();
    startProcess(
        Duration.ofSeconds(11),
        r -> {
          assertThat(r.phase()).isEqualTo(Phase.COMPLETED);
          assertThat(r.patientsSkippedCount()).isEqualTo(1);
        });
  }

  @Test
  void hdsReturnsWrongContentType() {
    mockDataSelector.whenFetchData(patientId).respondWithWrongContentType();
    startProcess(
        Duration.ofSeconds(1),
        r -> {
          assertThat(r.phase()).isEqualTo(Phase.COMPLETED);
          assertThat(r.patientsSkippedCount()).isEqualTo(1);
        });
  }

  @Test
  void hdsReturnsEmptyBundle() {
    mockDataSelector.whenFetchData(patientId).respondWithEmptyBundle();
    startProcess(
        Duration.ofSeconds(1),
        r -> {
          assertThat(r.phase()).isEqualTo(Phase.COMPLETED);
          assertThat(r.patientsSkippedCount()).isEqualTo(1);
        });
  }
}
