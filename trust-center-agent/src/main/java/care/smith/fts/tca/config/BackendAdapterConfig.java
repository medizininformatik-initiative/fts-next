package care.smith.fts.tca.config;

import care.smith.fts.util.HttpClientConfig;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for pseudonymization backend adapter selection.
 *
 * <p>This configuration allows selection between different pseudonymization backends: gPAS, Vfps,
 * or entici. Only one backend can be active at a time.
 *
 * <p>Example configuration:
 *
 * <pre>
 * de-identification:
 *   backend:
 *     type: gpas  # or: vfps, entici
 *     gpas:
 *       fhir:
 *         baseUrl: http://gpas:8080/ttp-fhir/fhir/gpas
 *     vfps:
 *       address: dns:///vfps:8081
 *     entici:
 *       baseUrl: http://entici:8080
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "de-identification.backend")
@Getter
@Setter
public class BackendAdapterConfig {

  /** The type of backend to use for pseudonymization. */
  @NotNull private BackendType type = BackendType.GPAS;

  /** gPAS-specific configuration (used when type=gpas). */
  private GpasConfig gpas;

  /** Vfps-specific configuration (used when type=vfps). */
  private VfpsConfig vfps;

  /** entici-specific configuration (used when type=entici). */
  private EnticiConfig entici;

  /** Supported backend types for pseudonymization. */
  public enum BackendType {
    GPAS,
    VFPS,
    ENTICI
  }

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

  /** Configuration for Vfps (Very Fast Pseudonym Service) backend. */
  @Getter
  @Setter
  public static class VfpsConfig {
    /** The gRPC or REST address of the Vfps service. */
    private String address;

    /** Optional authentication configuration. */
    private HttpClientConfig auth;
  }

  /** Configuration for entici backend. */
  @Getter
  @Setter
  public static class EnticiConfig {
    /** The base URL of the entici service. */
    private String baseUrl;

    /** Optional HTTP client configuration. */
    private HttpClientConfig server;
  }

  /**
   * Gets the active backend configuration based on the selected type.
   *
   * @return Optional containing the active backend config, or empty if not configured
   */
  public Optional<Object> getActiveBackendConfig() {
    return switch (type) {
      case GPAS -> Optional.ofNullable(gpas);
      case VFPS -> Optional.ofNullable(vfps);
      case ENTICI -> Optional.ofNullable(entici);
    };
  }
}
