package care.smith.fts.cda;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNullElse;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("runner")
public record TransferProcessRunnerConfig(

    /* Number of patients whose data is fetched and deidentified ahead of the send stage. */
    Integer maxConcurrentPatients,

    /*
     * Maximum number of patient bundles sent to the RDA concurrently. This is the real bottleneck and
     * should match the target Blaze's transaction capacity. Must not exceed maxConcurrentPatients.
     */
    Integer maxSendConcurrency,
    Integer maxConcurrentProcesses,

    /*
     * Number of rails used to group patients and their consents during cohort selection. Bounds the
     * CPU-bound grouping work to a dedicated Reactor scheduler instead of the JVM-wide common
     * ForkJoinPool. Defaults to DEFAULT_COHORT_SELECTION_CONCURRENCY when unset.
     */
    Integer cohortSelectionConcurrency,
    Duration processTtl) {

  /** Default number of cohort selection rails when {@code cohortSelectionConcurrency} is unset. */
  public static final int DEFAULT_COHORT_SELECTION_CONCURRENCY = 4;

  public TransferProcessRunnerConfig {
    cohortSelectionConcurrency =
        requireNonNullElse(cohortSelectionConcurrency, DEFAULT_COHORT_SELECTION_CONCURRENCY);
    checkArgument(maxConcurrentPatients > 1, "runner.maxConcurrentPatients must be greater than 0");
    checkArgument(maxSendConcurrency > 1, "runner.maxSendConcurrency must be greater than 0");
    checkArgument(
        maxSendConcurrency <= maxConcurrentPatients,
        "runner.maxSendConcurrency must not exceed runner.maxConcurrentPatients");
    checkArgument(
        cohortSelectionConcurrency > 0, "runner.cohortSelectionConcurrency must be greater than 0");
  }
}
