package care.smith.fts.packager;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.packager.config.MockPseudonymizerTestConfiguration;
import care.smith.fts.packager.config.PseudonymizerConfig;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for {@link FhirPackagerApplication}.
 * 
 * <p>Tests Spring Boot application startup, configuration loading, and basic functionality.
 */
@SpringBootTest(classes = MockPseudonymizerTestConfiguration.class)
@ActiveProfiles("test")
@DisplayName("FhirPackagerApplication")
class FhirPackagerApplicationTest {

  @Autowired
  private PseudonymizerConfig pseudonymizerConfig;

  @Test
  @DisplayName("should load application context successfully")
  void shouldLoadApplicationContextSuccessfully() {
    // If this test runs, the application context loaded successfully
    assertThat(pseudonymizerConfig).isNotNull();
  }

  @Test
  @DisplayName("should load configuration with test values")
  void shouldLoadConfigurationWithTestValues() {
    assertThat(pseudonymizerConfig.url()).isEqualTo("http://localhost:8080");
    assertThat(pseudonymizerConfig.readTimeout()).isEqualTo(Duration.parse("PT30S"));
    assertThat(pseudonymizerConfig.retry().maxAttempts()).isEqualTo(3);
    assertThat(pseudonymizerConfig.healthCheckEnabled()).isFalse();
  }
}