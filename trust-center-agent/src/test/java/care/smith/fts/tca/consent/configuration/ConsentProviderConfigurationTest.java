package care.smith.fts.tca.consent.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ConsentProviderConfigurationTest {

  @Autowired ConsentProviderConfiguration consentProviderConfiguration;

  @Test
  void consentProvider() {
    assertNotNull(consentProviderConfiguration);
  }

  @Test
  void getPolicySystem() {
    assertNotNull(consentProviderConfiguration.getPolicySystem());
  }

  @Test
  void getPatientIdentifierSystem() {
    assertNotNull(consentProviderConfiguration.getPatientIdentifierSystem());
  }

  @Test
  void getDefaultPolicies() {
    assertNotNull(consentProviderConfiguration.getDefaultPolicies());
  }
}
