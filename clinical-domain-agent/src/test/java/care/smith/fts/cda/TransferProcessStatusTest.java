package care.smith.fts.cda;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.cda.TransferProcessRunner.Phase;
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

    assertThat(status.isCompleted(status.phase())).isFalse();

    status = status.setPhase(Phase.COMPLETED);
    assertThat(status.isCompleted(status.phase())).isTrue();

    status = status.setPhase(Phase.COMPLETED_WITH_ERROR);
    assertThat(status.isCompleted(status.phase())).isTrue();

    status = status.setPhase(Phase.FATAL);
    assertThat(status.isCompleted(status.phase())).isTrue();
  }
}
