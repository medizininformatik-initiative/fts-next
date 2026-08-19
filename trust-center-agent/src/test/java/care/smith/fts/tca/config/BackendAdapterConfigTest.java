package care.smith.fts.tca.config;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.tca.config.BackendAdapterConfig.BackendType;
import care.smith.fts.tca.config.BackendAdapterConfig.EnticiConfig;
import care.smith.fts.tca.config.BackendAdapterConfig.GpasConfig;
import care.smith.fts.tca.config.BackendAdapterConfig.VfpsConfig;
import care.smith.fts.util.HttpClientConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BackendAdapterConfigTest {

  private BackendAdapterConfig config;

  @BeforeEach
  void setUp() {
    config = new BackendAdapterConfig();
  }

  @Test
  void defaultTypeIsGpas() {
    assertThat(config.getType()).isEqualTo(BackendType.GPAS);
  }

  @Test
  void setAndGetType() {
    config.setType(BackendType.VFPS);
    assertThat(config.getType()).isEqualTo(BackendType.VFPS);

    config.setType(BackendType.ENTICI);
    assertThat(config.getType()).isEqualTo(BackendType.ENTICI);
  }

  @Test
  void getActiveBackendConfigReturnsGpasWhenConfigured() {
    var gpasConfig = new GpasConfig();
    gpasConfig.setFhir(new HttpClientConfig("http://gpas:8080"));
    config.setGpas(gpasConfig);
    config.setType(BackendType.GPAS);

    var active = config.getActiveBackendConfig();

    assertThat(active).isPresent();
    assertThat(active.get()).isInstanceOf(GpasConfig.class);
  }

  @Test
  void getActiveBackendConfigReturnsVfpsWhenConfigured() {
    var vfpsConfig = new VfpsConfig();
    vfpsConfig.setAddress("dns:///vfps:8081");
    config.setVfps(vfpsConfig);
    config.setType(BackendType.VFPS);

    var active = config.getActiveBackendConfig();

    assertThat(active).isPresent();
    assertThat(active.get()).isInstanceOf(VfpsConfig.class);
  }

  @Test
  void getActiveBackendConfigReturnsEnticiWhenConfigured() {
    var enticiConfig = new EnticiConfig();
    enticiConfig.setBaseUrl("http://entici:8080");
    config.setEntici(enticiConfig);
    config.setType(BackendType.ENTICI);

    var active = config.getActiveBackendConfig();

    assertThat(active).isPresent();
    assertThat(active.get()).isInstanceOf(EnticiConfig.class);
  }

  @Test
  void getActiveBackendConfigReturnsEmptyWhenNotConfigured() {
    config.setType(BackendType.GPAS);
    config.setGpas(null);

    var active = config.getActiveBackendConfig();

    assertThat(active).isEmpty();
  }

  @Test
  void gpasConfigCanSetFhir() {
    var gpasConfig = new GpasConfig();
    var httpConfig = new HttpClientConfig("http://gpas:8080");
    gpasConfig.setFhir(httpConfig);

    assertThat(gpasConfig.getFhir()).isEqualTo(httpConfig);
  }

  @Test
  void vfpsConfigCanSetAddressAndAuth() {
    var vfpsConfig = new VfpsConfig();
    vfpsConfig.setAddress("dns:///vfps:8081");
    vfpsConfig.setAuth(new HttpClientConfig("http://auth:8080"));

    assertThat(vfpsConfig.getAddress()).isEqualTo("dns:///vfps:8081");
    assertThat(vfpsConfig.getAuth()).isNotNull();
  }

  @Test
  void enticiConfigCanSetBaseUrlAndServer() {
    var enticiConfig = new EnticiConfig();
    enticiConfig.setBaseUrl("http://entici:8080");
    enticiConfig.setServer(new HttpClientConfig("http://server:8080"));

    assertThat(enticiConfig.getBaseUrl()).isEqualTo("http://entici:8080");
    assertThat(enticiConfig.getServer()).isNotNull();
  }

  @Test
  void backendTypeEnumHasAllValues() {
    assertThat(BackendType.values())
        .containsExactly(BackendType.GPAS, BackendType.VFPS, BackendType.ENTICI);
  }
}
