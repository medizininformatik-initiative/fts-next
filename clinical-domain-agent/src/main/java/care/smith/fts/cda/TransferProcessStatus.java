package care.smith.fts.cda;

import static com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING;
import static lombok.AccessLevel.PRIVATE;

import care.smith.fts.cda.TransferProcessRunner.Phase;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import lombok.With;

public record TransferProcessStatus(
    String processId,
    @With(PRIVATE) Phase phase,
    @JsonFormat(shape = STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX") Instant createdAt,
    @With(PRIVATE) @JsonFormat(shape = STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        Instant finishedAt,
    @With(PRIVATE) long totalPatients,
    @With(PRIVATE) long totalBundles,
    @With(PRIVATE) long deidentifiedBundles,
    @With(PRIVATE) long sentBundles,
    @With(PRIVATE) long skippedBundles) {

  public static TransferProcessStatus create(String processId) {
    return new TransferProcessStatus(processId, Phase.QUEUED, Instant.now(), null, 0, 0, 0, 0, 0);
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
    return isCompleted(phase) ? withPhase(phase).withFinishedAt(Instant.now()) : withPhase(phase);
  }

  public static Boolean isCompleted(Phase phase) {
    return phase == Phase.COMPLETED || phase == Phase.COMPLETED_WITH_ERROR || phase == Phase.FATAL;
  }

  public Boolean mayBeRemoved(Instant before) {
    return finishedAt != null && finishedAt.isBefore(before);
  }
}
