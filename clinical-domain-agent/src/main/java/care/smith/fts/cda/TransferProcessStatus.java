package care.smith.fts.cda;

import static lombok.AccessLevel.PRIVATE;

import care.smith.fts.cda.TransferProcessRunner.Phase;
import java.time.LocalDateTime;
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
    @With(PRIVATE) long skippedBundles) {
  public static TransferProcessStatus create(String processId) {
    return new TransferProcessStatus(
        processId, Phase.QUEUED, LocalDateTime.now(), null, 0, 0, 0, 0, 0);
  }

  public TransferProcessStatus incTotalPatients() {
    return withTotalPatients(totalPatients() + 1);
  }

  public TransferProcessStatus incTotalBundles() {
    return withTotalBundles(totalBundles() + 1);
  }

  public TransferProcessStatus incDeidentifiedBundles() {
    return withDeidentifiedBundles(deidentifiedBundles() + 1);
  }

  public TransferProcessStatus incSentBundles() {
    return withSentBundles(sentBundles() + 1);
  }

  public TransferProcessStatus incSkippedBundles() {
    return withSkippedBundles(skippedBundles() + 1);
  }

  /**
   * Set the process phase. If the phase switches to COMPLETED or COMPLETED_WITH_ERROR `finishedAt`
   * is set. Once the process is in a completed state going back is not possible anymore.
   *
   * @param phase the next phase
   * @return TransferProcessStatus
   */
  public TransferProcessStatus setPhase(Phase phase) {
    return !anyCompleted(this.phase) ? setAnyCompleted(phase) : this;
  }

  private TransferProcessStatus setAnyCompleted(Phase phase) {
    return anyCompleted(phase)
        ? withPhase(phase).withFinishedAt(LocalDateTime.now())
        : withPhase(phase);
  }

  private Boolean anyCompleted(Phase phase) {
    return phase == Phase.COMPLETED || phase == Phase.COMPLETED_WITH_ERROR;
  }
}
