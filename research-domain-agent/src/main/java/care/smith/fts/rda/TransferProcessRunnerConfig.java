package care.smith.fts.rda;

import static com.google.common.base.Preconditions.checkArgument;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("runner")
public record TransferProcessRunnerConfig(
    int maxConcurrentTransactions, int retryAfterSeconds, Duration processTtl) {

  public TransferProcessRunnerConfig {
    checkArgument(
        maxConcurrentTransactions >= 1,
        "runner.maxConcurrentTransactions must be greater than or equal to 1");
    checkArgument(
        retryAfterSeconds >= 1, "runner.retryAfterSeconds must be greater than or equal to 1");
    checkArgument(
        processTtl != null && processTtl.isPositive(),
        "runner.processTtl must be a positive duration");
  }
}
