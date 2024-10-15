package care.smith.fts.cda;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("runner")
public class TransferProcessRunnerConfig {
  @NotNull int maxConcurrency;
  @NotNull int maxProcesses;
}
