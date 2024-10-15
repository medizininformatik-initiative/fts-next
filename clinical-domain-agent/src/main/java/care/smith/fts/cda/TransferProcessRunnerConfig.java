package care.smith.fts.cda;

import jakarta.validation.constraints.NotNull;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("runner")
@Setter
public class TransferProcessRunnerConfig {
  @NotNull int maxSendConcurrency;
  @NotNull int maxConcurrentProcesses;
  @NotNull int processTtlSeconds;
}
