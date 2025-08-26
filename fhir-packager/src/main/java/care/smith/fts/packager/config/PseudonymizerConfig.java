package care.smith.fts.packager.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the FHIR Pseudonymizer service integration.
 * 
 * <p>This class defines the configuration options for connecting to and interacting 
 * with the external FHIR Pseudonymizer REST service. Configuration values are loaded 
 * from application properties with the prefix "pseudonymizer".
 */
@Configuration
@ConfigurationProperties("pseudonymizer")
@Getter
@Setter
public class PseudonymizerConfig {

  /**
   * URL of the FHIR Pseudonymizer service.
   * The service endpoint for pseudonymization operations.
   */
  @NotNull
  private String url = "http://localhost:8080";

  /**
   * Request timeout duration for HTTP calls to the pseudonymizer service.
   * Uses ISO-8601 duration format (e.g., PT30S for 30 seconds).
   */
  @NotNull
  private Duration timeout = Duration.parse("PT30S");

  /**
   * Number of retry attempts for failed requests to the pseudonymizer service.
   * Must be at least 0 (no retries).
   */
  @Min(0)
  private int retries = 3;
}