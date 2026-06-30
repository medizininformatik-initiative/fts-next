package care.smith.fts.cda;

import static com.google.common.base.Preconditions.checkArgument;

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
    Duration processTtl,

    /*
     * Maximum number of patients whose transport mappings are sent to the TCA in one request. Larger
     * batches reduce the number of gPAS pseudonym calls but hold more deidentified bundles in memory.
     */
    Integer deidentifyBatchSize,

    /*
     * Maximum time to wait for a deidentify batch to fill before dispatching it as-is. Bounds the
     * latency added to small or trailing cohorts that never reach deidentifyBatchSize.
     */
    Duration deidentifyBatchTimeout) {

  public TransferProcessRunnerConfig {
    checkArgument(maxConcurrentPatients > 1, "runner.maxConcurrentPatients must be greater than 0");
    checkArgument(maxSendConcurrency > 1, "runner.maxSendConcurrency must be greater than 0");
    checkArgument(
        maxSendConcurrency <= maxConcurrentPatients,
        "runner.maxSendConcurrency must not exceed runner.maxConcurrentPatients");
    checkArgument(deidentifyBatchSize > 0, "runner.deidentifyBatchSize must be greater than 0");
    checkArgument(
        deidentifyBatchTimeout != null
            && !deidentifyBatchTimeout.isZero()
            && !deidentifyBatchTimeout.isNegative(),
        "runner.deidentifyBatchTimeout must be positive");
  }
}
