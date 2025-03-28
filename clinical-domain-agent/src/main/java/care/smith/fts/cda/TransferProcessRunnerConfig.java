package care.smith.fts.cda;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties("runner")
@Setter
public class TransferProcessRunnerConfig {

  @NestedConfigurationProperty @NotNull int maxSendConcurrency = 128;

  @NotNull int maxConcurrentProcesses = 4;
  @NotNull Duration processTtl = Duration.ofDays(1);
}
