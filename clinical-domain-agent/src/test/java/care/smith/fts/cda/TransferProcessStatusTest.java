package care.smith.fts.cda;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.cda.TransferProcessRunner.Phase;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
    Instant pastDate = Instant.now().plus(Duration.ofDays(1));

    // Initially finishedAt is null
    assertThat(status.mayBeRemoved(pastDate)).isFalse();

    // Set finishedAt
    status = status.setPhase(Phase.COMPLETED);
    assertThat(status.mayBeRemoved(pastDate)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = Phase.class,
      names = {"COMPLETED", "COMPLETED_WITH_ERROR", "FATAL"})
  void isCompletedIsTrueForEveryCompletedPhase(Phase phase) {
    assertThat(TransferProcessStatus.isCompleted(phase)).isTrue();
  }

  @ParameterizedTest
  @EnumSource(
      value = Phase.class,
      names = {"QUEUED", "RUNNING"})
  void isCompletedIsFalseForEveryUnfinishedPhase(Phase phase) {
    assertThat(TransferProcessStatus.isCompleted(phase)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(
      value = Phase.class,
      names = {"COMPLETED", "COMPLETED_WITH_ERROR", "FATAL"})
  void setPhaseSetsFinishedAtForEveryCompletedPhase(Phase phase) {
    var status = TransferProcessStatus.create("process123").setPhase(phase);

    assertThat(status.phase()).isEqualTo(phase);
    assertThat(status.finishedAt()).isNotNull();
  }

  @Test
  void setPhaseLeavesFinishedAtUnsetWhileRunning() {
    var status = TransferProcessStatus.create("process123").setPhase(Phase.RUNNING);

    assertThat(status.phase()).isEqualTo(Phase.RUNNING);
    assertThat(status.finishedAt()).isNull();
  }

  @Test
  void testIncDeidentifiedBundles() {
    var status = TransferProcessStatus.create("process123").incDeidentifiedBundles();

    assertThat(status.deidentifiedBundles()).isEqualTo(1);
  }
}
