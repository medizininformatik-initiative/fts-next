package care.smith.fts.tca.services;

import care.smith.fts.tca.adapters.GpasBackendAdapter;
import care.smith.fts.tca.adapters.PseudonymBackendAdapter;
import care.smith.fts.tca.config.BackendAdapterConfig;
import care.smith.fts.tca.config.BackendAdapterConfig.BackendType;
import care.smith.fts.tca.deidentification.GpasClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Factory for creating pseudonymization backend adapters.
 *
 * <p>This factory creates the appropriate {@link PseudonymBackendAdapter} based on the configured
 * backend type. Currently supports:
 *
 * <ul>
 *   <li>gPAS - Uses existing {@link GpasClient} implementation
 *   <li>Vfps - Placeholder for future implementation
 *   <li>entici - Placeholder for future implementation
 * </ul>
 *
 * <p>The factory is configured via {@link BackendAdapterConfig} which reads from application.yaml:
 *
 * <pre>
 * de-identification:
 *   backend:
 *     type: gpas  # or: vfps, entici
 * </pre>
 */
@Slf4j
@Component
public class BackendAdapterFactory {

  private final BackendAdapterConfig config;
  private final GpasClient gpasClient;

  public BackendAdapterFactory(BackendAdapterConfig config, GpasClient gpasClient) {
    this.config = config;
    this.gpasClient = gpasClient;
  }

  /**
   * Creates a backend adapter based on the configured type.
   *
   * @return the configured PseudonymBackendAdapter
   * @throws UnsupportedOperationException if the backend type is not yet implemented
   */
  public PseudonymBackendAdapter createAdapter() {
    var type = config.getType();
    log.info("Creating backend adapter for type: {}", type);

    return switch (type) {
      case GPAS -> createGpasAdapter();
      case VFPS -> createVfpsAdapter();
      case ENTICI -> createEnticiAdapter();
    };
  }

  private PseudonymBackendAdapter createGpasAdapter() {
    log.debug("Initializing gPAS backend adapter");
    return new GpasBackendAdapter(gpasClient);
  }

  private PseudonymBackendAdapter createVfpsAdapter() {
    // Placeholder for Vfps implementation
    throw new UnsupportedOperationException(
        "Vfps backend adapter is not yet implemented. Use backend.type=gpas for now.");
  }

  private PseudonymBackendAdapter createEnticiAdapter() {
    // Placeholder for entici implementation
    throw new UnsupportedOperationException(
        "entici backend adapter is not yet implemented. Use backend.type=gpas for now.");
  }

  /**
   * Gets the currently configured backend type.
   *
   * @return the backend type
   */
  public BackendType getConfiguredBackendType() {
    return config.getType();
  }
}
