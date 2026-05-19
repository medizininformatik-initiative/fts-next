package care.smith.fts.tca.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import care.smith.fts.tca.adapters.EnticiBackendAdapter;
import care.smith.fts.tca.adapters.GpasBackendAdapter;
import care.smith.fts.tca.adapters.VfpsBackendAdapter;
import care.smith.fts.tca.config.BackendAdapterConfig;
import care.smith.fts.tca.config.BackendAdapterConfig.BackendType;
import care.smith.fts.tca.deidentification.EnticiClient;
import care.smith.fts.tca.deidentification.GpasClient;
import care.smith.fts.tca.deidentification.VfpsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BackendAdapterFactoryTest {

  @Mock private BackendAdapterConfig config;
  @Mock private GpasClient gpasClient;
  @Mock private VfpsClient vfpsClient;
  @Mock private EnticiClient enticiClient;

  private BackendAdapterFactory factory;

  @BeforeEach
  void setUp() {
    factory = new BackendAdapterFactory(config, gpasClient, vfpsClient, enticiClient);
  }

  @Test
  void createAdapterReturnsGpasAdapter() {
    when(config.getType()).thenReturn(BackendType.GPAS);

    var adapter = factory.createAdapter();

    assertThat(adapter).isInstanceOf(GpasBackendAdapter.class);
    assertThat(adapter.getBackendType()).isEqualTo("gpas");
  }

  @Test
  void createAdapterReturnsVfpsAdapter() {
    when(config.getType()).thenReturn(BackendType.VFPS);

    var adapter = factory.createAdapter();

    assertThat(adapter).isInstanceOf(VfpsBackendAdapter.class);
    assertThat(adapter.getBackendType()).isEqualTo("vfps");
  }

  @Test
  void createAdapterReturnsEnticiAdapter() {
    when(config.getType()).thenReturn(BackendType.ENTICI);

    var adapter = factory.createAdapter();

    assertThat(adapter).isInstanceOf(EnticiBackendAdapter.class);
    assertThat(adapter.getBackendType()).isEqualTo("entici");
  }

  @Test
  void createAdapterThrowsForVfpsWhenClientNotConfigured() {
    var factoryWithoutVfps = new BackendAdapterFactory(config, gpasClient, null, enticiClient);
    when(config.getType()).thenReturn(BackendType.VFPS);

    assertThatThrownBy(factoryWithoutVfps::createAdapter)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("VfpsClient is not configured");
  }

  @Test
  void createAdapterThrowsForEnticiWhenClientNotConfigured() {
    var factoryWithoutEntici = new BackendAdapterFactory(config, gpasClient, vfpsClient, null);
    when(config.getType()).thenReturn(BackendType.ENTICI);

    assertThatThrownBy(factoryWithoutEntici::createAdapter)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("EnticiClient is not configured");
  }

  @Test
  void getConfiguredBackendTypeReturnsConfiguredType() {
    when(config.getType()).thenReturn(BackendType.GPAS);

    assertThat(factory.getConfiguredBackendType()).isEqualTo(BackendType.GPAS);
  }

  @Test
  void getConfiguredBackendTypeReflectsConfigChanges() {
    when(config.getType()).thenReturn(BackendType.VFPS);

    assertThat(factory.getConfiguredBackendType()).isEqualTo(BackendType.VFPS);
  }
}
