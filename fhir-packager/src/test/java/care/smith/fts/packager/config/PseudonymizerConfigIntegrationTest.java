package care.smith.fts.packager.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Integration tests for {@link PseudonymizerConfig} property binding.
 * 
 * <p>Tests that configuration properties are correctly bound from various sources
 * including system properties, environment variables, and application properties.
 */
@SpringBootTest
@DisplayName("PseudonymizerConfig Integration")
class PseudonymizerConfigIntegrationTest {

  @SpringBootTest
  @TestPropertySource(properties = {
    "pseudonymizer.url=https://custom-host:9090",
    "pseudonymizer.timeout=PT45S",
    "pseudonymizer.retries=5"
  })
  @DisplayName("Custom Properties")
  static class CustomPropertiesTest {

    @Autowired
    private PseudonymizerConfig config;

    @Test
    @DisplayName("should bind custom properties correctly")
    void shouldBindCustomPropertiesCorrectly() {
      assertThat(config.getUrl()).isEqualTo("https://custom-host:9090");
      assertThat(config.getTimeout()).isEqualTo(Duration.parse("PT45S"));
      assertThat(config.getRetries()).isEqualTo(5);
    }
  }

  @SpringBootTest
  @TestPropertySource(properties = {
    "pseudonymizer.retries=0"
  })
  @DisplayName("Zero Retries")
  static class ZeroRetriesTest {

    @Autowired
    private PseudonymizerConfig config;

    @Test
    @DisplayName("should allow zero retries configuration")
    void shouldAllowZeroRetriesConfiguration() {
      assertThat(config.getRetries()).isEqualTo(0);
    }
  }

  @SpringBootTest
  @TestPropertySource(properties = {
    "pseudonymizer.timeout=PT1M30S"
  })
  @DisplayName("Complex Duration")
  static class ComplexDurationTest {

    @Autowired
    private PseudonymizerConfig config;

    @Test
    @DisplayName("should parse complex duration correctly")
    void shouldParseComplexDurationCorrectly() {
      assertThat(config.getTimeout()).isEqualTo(Duration.parse("PT1M30S"));
      assertThat(config.getTimeout().toSeconds()).isEqualTo(90);
    }
  }
}