package care.smith.fts.tca.services;

import care.smith.fts.tca.adapters.EnticiBackendAdapter;
import care.smith.fts.tca.adapters.GpasBackendAdapter;
import care.smith.fts.tca.adapters.PseudonymBackendAdapter;
import care.smith.fts.tca.adapters.VfpsBackendAdapter;
import care.smith.fts.tca.config.BackendAdapterConfig;
import care.smith.fts.tca.config.BackendAdapterConfig.BackendType;
import care.smith.fts.tca.deidentification.EnticiClient;
import care.smith.fts.tca.deidentification.GpasClient;
import care.smith.fts.tca.deidentification.VfpsClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Factory for creating pseudonymization backend adapters.
 *
 * <p>This factory creates the appropriate {@link PseudonymBackendAdapter} based on the configured
 * backend type. Currently supports:
 *
 * <ul>
 *   <li>gPAS - Uses existing {@link GpasClient} implementation
 *   <li>Vfps - Uses {@link VfpsClient} for Very Fast Pseudonym Service
 *   <li>entici - Uses {@link EnticiClient} for Entici pseudonymization service
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
  private final VfpsClient vfpsClient;
  private final EnticiClient enticiClient;

  public BackendAdapterFactory(
      BackendAdapterConfig config,
      GpasClient gpasClient,
      @Autowired(required = false) VfpsClient vfpsClient,
      @Autowired(required = false) EnticiClient enticiClient) {
    this.config = config;
    this.gpasClient = gpasClient;
    this.vfpsClient = vfpsClient;
    this.enticiClient = enticiClient;
  }

  /**
   * Creates a backend adapter based on the configured type.
   *
   * @return the configured PseudonymBackendAdapter
   * @throws IllegalStateException if the required client is not configured
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
    if (vfpsClient == null) {
      throw new IllegalStateException(
          "Vfps backend selected but VfpsClient is not configured. "
              + "Add de-identification.vfps.fhir.base-url to configuration.");
    }
    log.debug("Initializing Vfps backend adapter");
    return new VfpsBackendAdapter(vfpsClient);
  }

  private PseudonymBackendAdapter createEnticiAdapter() {
    if (enticiClient == null) {
      throw new IllegalStateException(
          "Entici backend selected but EnticiClient is not configured. "
              + "Add de-identification.entici.fhir.base-url to configuration.");
    }
    log.debug("Initializing Entici backend adapter");
    return new EnticiBackendAdapter(enticiClient);
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
