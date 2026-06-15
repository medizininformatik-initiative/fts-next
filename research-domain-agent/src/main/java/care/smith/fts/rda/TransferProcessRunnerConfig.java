package care.smith.fts.rda;

import static com.google.common.base.Preconditions.checkArgument;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("runner")
public record TransferProcessRunnerConfig(int maxConcurrentTransactions, int retryAfterSeconds) {

  public TransferProcessRunnerConfig {
    checkArgument(
        maxConcurrentTransactions >= 1,
        "runner.maxConcurrentTransactions must be greater than or equal to 1");
    checkArgument(
        retryAfterSeconds >= 1, "runner.retryAfterSeconds must be greater than or equal to 1");
  }
}
