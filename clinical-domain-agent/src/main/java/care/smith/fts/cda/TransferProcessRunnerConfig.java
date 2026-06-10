package care.smith.fts.cda;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties("runner")
@Validated
@Setter
public class TransferProcessRunnerConfig {

  /** Number of patients whose data is fetched and deidentified ahead of the send stage. */
  @NotNull
  @Min(1)
  int maxConcurrentPatients = 8;

  /**
   * Maximum number of patient bundles sent to the RDA concurrently. This is the real bottleneck and
   * should match the target Blaze's transaction capacity. Must not exceed maxConcurrentPatients.
   */
  @NotNull
  @Min(1)
  int maxSendConcurrency = 2;

  @NotNull int maxConcurrentProcesses = 4;
  @NotNull Duration processTtl = Duration.ofDays(1);

  @AssertTrue(message = "runner.maxSendConcurrency must not exceed runner.maxConcurrentPatients")
  boolean isSendConcurrencyWithinPrefetchWindow() {
    return maxSendConcurrency <= maxConcurrentPatients;
  }
}
