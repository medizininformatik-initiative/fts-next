package care.smith.fts.rda;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Tunables for the RDA backpressure runner.
 *
 * <ul>
 *   <li>{@code globalBufferMax} bounds the total number of bundles in the system (queued +
 *       in-flight) across all projects — tier-1 of the admission policy.
 *   <li>{@code retryAfterSeconds} is the {@code Retry-After} hint returned with a 429 when
 *       admission rejects a bundle.
 * </ul>
 */
@Configuration
@ConfigurationProperties("rda")
@Getter
@Setter
public class RdaRunnerConfig {

  @NotNull int globalBufferMax = 256;

  @NotNull int retryAfterSeconds = 5;
}
