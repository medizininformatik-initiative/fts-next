package care.smith.fts.tca.consent;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class GicsFhirConfigurationTest {

  @Autowired private GicsFhirConfiguration gicsFhirConfiguration;
  @Autowired private FhirConsentProvider fhirConsentProvider;

  @Test
  void getBaseUrl() {
    assertNotNull(gicsFhirConfiguration.getBaseUrl());
  }

  @Test
  void getAuth() {
    assertNotNull(gicsFhirConfiguration.getAuth());
  }

  @Test
  void fhirConsentProvider() {
    assertNotNull(fhirConsentProvider);
  }
}
