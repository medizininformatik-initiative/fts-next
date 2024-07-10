package care.smith.fts.cda.rest.it;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.cda.TransferProcessRunner.Status;
import java.io.IOException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class FhirResolveServiceIT extends TransferProcessControllerIT {
  private static final String patientId = "id1";

  @BeforeEach
  void setUp() throws IOException {
    mockCohortSelector.successOnePatient(patientId);
  }

  @Test
  void hdsDown() {
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).isDown();
    startProcess(
        Duration.ofSeconds(1),
        r -> {
          assertThat(r.status()).isEqualTo(Status.COMPLETED);
          assertThat(r.patientsSkippedCount()).isEqualTo(1);
        });
  }

  @Test
  void hdsTimeout() {
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).timeout();
    startProcess(
        Duration.ofSeconds(11),
        r -> {
          assertThat(r.status()).isEqualTo(Status.COMPLETED);
          assertThat(r.patientsSkippedCount()).isEqualTo(1);
        });
  }

  @Test
  void hdsReturnsWrongContentType() {
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).wrongContentType();
    startProcess(
        Duration.ofSeconds(1),
        r -> {
          assertThat(r.status()).isEqualTo(Status.COMPLETED);
          assertThat(r.patientsSkippedCount()).isEqualTo(1);
        });
  }

  @Test
  void hdsReturnsMoreThanOneResult() throws IOException {
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).moreThanOneResult();
    startProcess(
        Duration.ofSeconds(1),
        r -> {
          assertThat(r.status()).isEqualTo(Status.COMPLETED);
          assertThat(r.patientsSkippedCount()).isEqualTo(1);
        });
  }

  @Test
  void hdsReturnsEmptyBundle() {
    mockDataSelector.whenResolvePatient(patientId, DEFAULT_IDENTIFIER_SYSTEM).emptyBundle();
    startProcess(
        Duration.ofSeconds(1),
        r -> {
          assertThat(r.status()).isEqualTo(Status.COMPLETED);
          assertThat(r.patientsSkippedCount()).isEqualTo(1);
        });
  }
}
