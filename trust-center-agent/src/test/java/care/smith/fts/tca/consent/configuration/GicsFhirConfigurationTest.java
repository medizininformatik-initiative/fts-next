package care.smith.fts.tca.consent.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.tca.consent.FhirConsentProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class GicsFhirConfigurationTest {

  @Autowired private GicsFhirConfiguration gicsFhirConfiguration;
  @Autowired private FhirConsentProvider fhirConsentProvider;

  @Test
  void getBaseUrl() {
    assertThat(gicsFhirConfiguration.getBaseUrl()).isNotNull();
  }

  /*
   * The page size is set to 200 in test/resources/application.yaml
   * */
  @Test
  void getPageSize() {
    assertThat(gicsFhirConfiguration.getDefaultPageSize()).isEqualTo(200);
  }

  @Test
  void getAuth() {
    assertThat(gicsFhirConfiguration.getAuth()).isNotNull();
  }

  @Test
  void fhirConsentProvider() {
    assertThat(fhirConsentProvider).isNotNull();
  }
}
