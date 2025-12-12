package care.smith.fts.tca.config;

import care.smith.fts.util.HttpClientConfig;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the gPAS pseudonymization backend.
 *
 * <p>Example configuration:
 *
 * <pre>
 * de-identification:
 *   backend:
 *     gpas:
 *       fhir:
 *         baseUrl: http://gpas:8080/ttp-fhir/fhir/gpas
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "de-identification.backend")
@Getter
@Setter
public class BackendAdapterConfig {

  /** gPAS-specific configuration. */
  private GpasConfig gpas;

  /**
   * Configuration for gPAS backend.
   *
   * <p>Note: gPAS configuration is already handled by GpasDeIdentificationConfiguration. This
   * provides an alternative path for the backend adapter pattern.
   */
  @Getter
  @Setter
  public static class GpasConfig {
    private HttpClientConfig fhir;
  }
}
