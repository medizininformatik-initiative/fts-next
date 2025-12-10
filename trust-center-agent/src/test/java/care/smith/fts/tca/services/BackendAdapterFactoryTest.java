package care.smith.fts.tca.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import care.smith.fts.tca.adapters.GpasBackendAdapter;
import care.smith.fts.tca.config.BackendAdapterConfig;
import care.smith.fts.tca.config.BackendAdapterConfig.BackendType;
import care.smith.fts.tca.deidentification.GpasClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BackendAdapterFactoryTest {

  @Mock private BackendAdapterConfig config;
  @Mock private GpasClient gpasClient;

  private BackendAdapterFactory factory;

  @BeforeEach
  void setUp() {
    factory = new BackendAdapterFactory(config, gpasClient);
  }

  @Test
  void createAdapterReturnsGpasAdapter() {
    when(config.getType()).thenReturn(BackendType.GPAS);

    var adapter = factory.createAdapter();

    assertThat(adapter).isInstanceOf(GpasBackendAdapter.class);
    assertThat(adapter.getBackendType()).isEqualTo("gpas");
  }

  @Test
  void createAdapterThrowsForVfps() {
    when(config.getType()).thenReturn(BackendType.VFPS);

    assertThatThrownBy(() -> factory.createAdapter())
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("Vfps backend adapter is not yet implemented");
  }

  @Test
  void createAdapterThrowsForEntici() {
    when(config.getType()).thenReturn(BackendType.ENTICI);

    assertThatThrownBy(() -> factory.createAdapter())
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("entici backend adapter is not yet implemented");
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
