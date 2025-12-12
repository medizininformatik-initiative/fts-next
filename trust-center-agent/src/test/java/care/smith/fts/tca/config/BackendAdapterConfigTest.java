package care.smith.fts.tca.config;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.tca.config.BackendAdapterConfig.GpasConfig;
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
  void gpasConfigIsNullByDefault() {
    assertThat(config.getGpas()).isNull();
  }

  @Test
  void setAndGetGpasConfig() {
    var gpasConfig = new GpasConfig();
    gpasConfig.setFhir(new HttpClientConfig("http://gpas:8080"));
    config.setGpas(gpasConfig);

    assertThat(config.getGpas()).isEqualTo(gpasConfig);
    assertThat(config.getGpas().getFhir().baseUrl()).isEqualTo("http://gpas:8080");
  }

  @Test
  void gpasConfigCanSetFhir() {
    var gpasConfig = new GpasConfig();
    var httpConfig = new HttpClientConfig("http://gpas:8080");
    gpasConfig.setFhir(httpConfig);

    assertThat(gpasConfig.getFhir()).isEqualTo(httpConfig);
  }
}
