package care.smith.fts.tca.consent.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
class GicsConfigurationIT {
  @Autowired private GicsConfiguration gicsConfiguration;

  @MockitoBean
  RedissonClient redisClient; // We need to mock the redisClient otherwise the tests won't start

  /*
   * The page size is set to 200 in test/resources/application.yaml
   * */
  @Test
  void getPageSize() {
    assertThat(gicsConfiguration.getPageSize()).isEqualTo(200);
  }

  @Test
  void getDefaultPageSizeIfPageSizeIsNull() {
    var localGicsConfiguration = new GicsConfiguration();
    localGicsConfiguration.setPageSize(null);
    var fhir = gicsConfiguration.getFhir();
    fhir.setDefaultPageSize(null);
    localGicsConfiguration.setFhir(fhir);

    assertThat(localGicsConfiguration.getPageSize()).isEqualTo(null);
    assertThat(localGicsConfiguration.pageSize()).isEqualTo(50);
  }

  @Test
  void getGicsFhirDefaultPageSizeIfPageSizeIsNull() {
    var localGicsConfiguration = new GicsConfiguration();
    localGicsConfiguration.setPageSize(null);

    var fhir = gicsConfiguration.getFhir();
    fhir.setDefaultPageSize(42);
    localGicsConfiguration.setFhir(fhir);

    assertThat(localGicsConfiguration.getPageSize()).isEqualTo(null);
    assertThat(localGicsConfiguration.pageSize()).isEqualTo(42);
  }

  @Test
  void gicsFhirDefaultPageSizeMayBeNull() {
    var localGicsConfiguration = new GicsConfiguration();
    var fhir = gicsConfiguration.getFhir();
    fhir.setDefaultPageSize(null);
    localGicsConfiguration.setFhir(fhir);
    assertThat(localGicsConfiguration.getFhir().defaultPageSize()).isEqualTo(null);
  }

  @Test
  void getBaseUrl() {
    assertThat(gicsConfiguration.fhir.getBaseUrl()).isNotNull();
  }
}
