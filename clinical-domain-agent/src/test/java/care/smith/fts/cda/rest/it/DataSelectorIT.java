package care.smith.fts.cda.rest.it;

import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DataSelectorIT extends TransferProcessControllerIT {
  private static final String patientId = "patientId";

  @BeforeEach
  void setUp() throws IOException {
    mockCohortSelector.consentForOnePatient(patientId);
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).success(patientId);
  }

  @Test
  void hdsDown() {
    mockDataSelector.whenFetchData(patientId).dropConnection();
    startProcessExpectCompletedWithSkipped(Duration.ofSeconds(1));
  }

  @Test
  void hdsTimeout() {
    mockDataSelector.whenFetchData(patientId).timeout();
    startProcessExpectCompletedWithSkipped(Duration.ofSeconds(11));
  }

  @Test
  void hdsReturnsWrongContentType() {
    mockDataSelector.whenFetchData(patientId).respondWithWrongContentType();
    startProcessExpectCompletedWithSkipped(Duration.ofSeconds(1));
  }

  @Test
  void hdsReturnsEmptyBundle() {
    mockDataSelector.whenFetchData(patientId).respondWithEmptyBundle();
    startProcessExpectCompletedWithSkipped(Duration.ofSeconds(1));
  }
}
