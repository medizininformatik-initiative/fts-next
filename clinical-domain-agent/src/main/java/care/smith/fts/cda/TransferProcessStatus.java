package care.smith.fts.cda;

import static lombok.AccessLevel.PRIVATE;
import static lombok.AccessLevel.PUBLIC;

import care.smith.fts.cda.TransferProcessRunner.Phase;
import lombok.With;

public record TransferProcessStatus(
    String processId,
    @With(PUBLIC) Phase phase,
    @With(PRIVATE) long totalPatients,
    @With(PRIVATE) long totalBundles,
    @With(PRIVATE) long deidentifiedBundles,
    @With(PRIVATE) long sentBundles,
    @With(PRIVATE) long skippedBundles) {
  public static TransferProcessStatus create(String processId) {
    return new TransferProcessStatus(processId, Phase.QUEUED, 0, 0, 0, 0, 0);
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
}
