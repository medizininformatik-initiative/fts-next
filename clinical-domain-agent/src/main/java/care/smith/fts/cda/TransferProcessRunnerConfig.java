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
    Duration processTtl) {

  public TransferProcessRunnerConfig {
    checkArgument(maxConcurrentPatients > 1, "runner.maxConcurrentPatients must be greater than 0");
    checkArgument(maxSendConcurrency > 1, "runner.maxSendConcurrency must be greater than 0");
    checkArgument(
        maxSendConcurrency <= maxConcurrentPatients,
        "runner.maxSendConcurrency must not exceed runner.maxConcurrentPatients");
  }
}
