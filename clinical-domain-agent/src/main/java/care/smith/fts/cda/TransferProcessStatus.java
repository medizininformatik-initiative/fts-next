package care.smith.fts.cda;

import static lombok.AccessLevel.PRIVATE;

import care.smith.fts.cda.TransferProcessRunner.Phase;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.With;

public record TransferProcessStatus(
    String processId,
    @With(PRIVATE) Phase phase,
    LocalDateTime createdAt,
    @With(PRIVATE) LocalDateTime finishedAt,
    @With(PRIVATE) long totalPatients,
    @With(PRIVATE) long totalBundles,
    @With(PRIVATE) long deidentifiedBundles,
    @With(PRIVATE) long sentBundles,
    @With(PRIVATE) long skippedBundles,
    @JsonIgnore @With(PRIVATE) List<PatientError> failedPatients) {

  public enum Step {
    SELECT_DATA("select data"),
    DEIDENTIFY("deidentify bundle"),
    SEND_BUNDLE("send bundle");

    private final String displayName;

    Step(String displayName) {
      this.displayName = displayName;
    }

    @Override
    public String toString() {
      return displayName;
    }
  }

  public record PatientError(String patientId, Step step, String errorMessage) {}

  public static TransferProcessStatus create(String processId) {
    return new TransferProcessStatus(
        processId, Phase.QUEUED, LocalDateTime.now(), null, 0, 0, 0, 0, 0, List.of());
  }

  public TransferProcessStatus incTotalPatients() {
    return withTotalPatients(totalPatients + 1);
  }

  public TransferProcessStatus incTotalBundles() {
    return withTotalBundles(totalBundles + 1);
  }

  public TransferProcessStatus incDeidentifiedBundles() {
    return withDeidentifiedBundles(deidentifiedBundles + 1);
  }

  public TransferProcessStatus incSentBundles() {
    return withSentBundles(sentBundles + 1);
  }

  public TransferProcessStatus incSkippedBundles() {
    return withSkippedBundles(skippedBundles + 1);
  }

  public TransferProcessStatus addFailedPatient(String patientId, Step step, String errorMessage) {
    var updated = new ArrayList<>(failedPatients);
    updated.add(new PatientError(patientId, step, errorMessage));
    return withFailedPatients(List.copyOf(updated));
  }

  /**
   * Set the process phase. If the phase switches to COMPLETED or COMPLETED_WITH_ERROR `finishedAt`
   * is set. Once the process is in a completed state going back is not possible anymore.
   *
   * @param phase the next phase
   * @return TransferProcessStatus
   */
  public TransferProcessStatus setPhase(Phase phase) {
    return !isCompleted(this.phase) ? setAnyCompleted(phase) : this;
  }

  private TransferProcessStatus setAnyCompleted(Phase phase) {
    return isCompleted(phase)
        ? withPhase(phase).withFinishedAt(LocalDateTime.now())
        : withPhase(phase);
  }

  public static Boolean isCompleted(Phase phase) {
    return phase == Phase.COMPLETED || phase == Phase.COMPLETED_WITH_ERROR || phase == Phase.FATAL;
  }

  public Boolean mayBeRemoved(LocalDateTime before) {
    return finishedAt != null && finishedAt.isBefore(before);
  }
}
