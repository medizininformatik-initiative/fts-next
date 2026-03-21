package care.smith.fts.cda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import care.smith.fts.cda.TransferProcessRunner.Phase;
import care.smith.fts.cda.TransferProcessStatus.PatientError;
import care.smith.fts.cda.TransferProcessStatus.Step;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class TransferProcessStatusTest {

  @Test
  void testCreate() {
    String processId = "process123";
    var status = TransferProcessStatus.create(processId);

    assertThat(status.processId()).isEqualTo(processId);
    assertThat(status.phase()).isEqualTo(Phase.QUEUED);
    assertThat(status.createdAt()).isNotNull();
    assertThat(status.finishedAt()).isNull();
    assertThat(status.totalPatients()).isEqualTo(0);
    assertThat(status.totalBundles()).isEqualTo(0);
    assertThat(status.deidentifiedBundles()).isEqualTo(0);
    assertThat(status.sentBundles()).isEqualTo(0);
    assertThat(status.skippedBundles()).isEqualTo(0);
    assertThat(status.failedPatients()).isEmpty();
  }

  @Test
  void testIncTotalPatients() {
    var status = TransferProcessStatus.create("process123");
    status = status.incTotalPatients();

    assertThat(status.totalPatients()).isEqualTo(1);
  }

  @Test
  void testIncTotalBundles() {
    var status = TransferProcessStatus.create("process123");
    status = status.incTotalBundles();

    assertThat(status.totalBundles()).isEqualTo(1);
  }

  @Test
  void testSetPhase() {
    var status = TransferProcessStatus.create("process123");
    status = status.setPhase(Phase.RUNNING);

    assertThat(status.phase()).isEqualTo(Phase.RUNNING);

    // Now set to a completed phase
    status = status.setPhase(Phase.COMPLETED);

    assertThat(status.phase()).isEqualTo(Phase.COMPLETED);
    assertThat(status.finishedAt()).isNotNull();
  }

  @Test
  void testSetPhaseAfterCompleted() {
    var status = TransferProcessStatus.create("process123");
    status = status.setPhase(Phase.COMPLETED);

    // Attempt to go back to IN_PROGRESS
    status = status.setPhase(Phase.RUNNING);

    assertThat(status.phase()).isEqualTo(Phase.COMPLETED); // Should still be COMPLETED
  }

  @Test
  void testMayBeRemoved() {
    var status = TransferProcessStatus.create("process123");
    LocalDateTime pastDate = LocalDateTime.now().plusDays(1);

    // Initially finishedAt is null
    assertThat(status.mayBeRemoved(pastDate)).isFalse();

    // Set finishedAt
    status = status.setPhase(Phase.COMPLETED);
    assertThat(status.mayBeRemoved(pastDate)).isTrue();
  }

  @Test
  void testIsCompleted() {
    var status = TransferProcessStatus.create("process123");

    assertThat(TransferProcessStatus.isCompleted(status.phase())).isFalse();

    status = status.setPhase(Phase.COMPLETED);
    assertThat(TransferProcessStatus.isCompleted(status.phase())).isTrue();

    status = status.setPhase(Phase.COMPLETED_WITH_ERROR);
    assertThat(TransferProcessStatus.isCompleted(status.phase())).isTrue();

    status = status.setPhase(Phase.FATAL);
    assertThat(TransferProcessStatus.isCompleted(status.phase())).isTrue();
  }

  @Test
  void addFailedPatientAddsEntry() {
    var status =
        TransferProcessStatus.create("process123")
            .addFailedPatient("patient-1", Step.SELECT_DATA, "Connection refused");

    assertThat(status.failedPatients()).hasSize(1);
    assertThat(status.failedPatients().getFirst().patientId()).isEqualTo("patient-1");
    assertThat(status.failedPatients().getFirst().step()).isEqualTo(Step.SELECT_DATA);
    assertThat(status.failedPatients().getFirst().errorMessage()).isEqualTo("Connection refused");
  }

  @Test
  void addMultipleFailedPatientsAccumulatesAll() {
    var status =
        TransferProcessStatus.create("process123")
            .addFailedPatient("patient-1", Step.SELECT_DATA, "Error A")
            .addFailedPatient("patient-2", Step.DEIDENTIFY, "Error B");

    assertThat(status.failedPatients()).hasSize(2);
    assertThat(status.failedPatients().get(0).patientId()).isEqualTo("patient-1");
    assertThat(status.failedPatients().get(0).step()).isEqualTo(Step.SELECT_DATA);
    assertThat(status.failedPatients().get(1).patientId()).isEqualTo("patient-2");
    assertThat(status.failedPatients().get(1).step()).isEqualTo(Step.DEIDENTIFY);
  }

  @Test
  void addFailedPatientPreservesImmutability() {
    var original = TransferProcessStatus.create("process123");
    var withError = original.addFailedPatient("patient-1", Step.SEND_BUNDLE, "Error");

    assertThat(original.failedPatients()).isEmpty();
    assertThat(withError.failedPatients()).hasSize(1);
  }

  @Test
  void failedPatientsListIsUnmodifiable() {
    var status =
        TransferProcessStatus.create("process123")
            .addFailedPatient("patient-1", Step.SELECT_DATA, "Error");

    assertThatThrownBy(
            () -> status.failedPatients().add(new PatientError("x", Step.SELECT_DATA, "y")))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
