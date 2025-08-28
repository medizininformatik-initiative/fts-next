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
class PseudonymizerConfigIT {

  @SpringBootTest
  @TestPropertySource(properties = {
    "pseudonymizer.url=https://custom-host:9090",
    "pseudonymizer.connect-timeout=PT15S",
    "pseudonymizer.read-timeout=PT45S",
    "pseudonymizer.retry.max-attempts=5"
  })
  @DisplayName("Custom Properties")
  static class CustomPropertiesTest {

    @Autowired
    private PseudonymizerConfig config;

    @Test
    @DisplayName("should bind custom properties correctly")
    void shouldBindCustomPropertiesCorrectly() {
      assertThat(config.url()).isEqualTo("https://custom-host:9090");
      assertThat(config.connectTimeout()).isEqualTo(Duration.parse("PT15S"));
      assertThat(config.readTimeout()).isEqualTo(Duration.parse("PT45S"));
      assertThat(config.retry().maxAttempts()).isEqualTo(5);
    }
  }

  @SpringBootTest
  @TestPropertySource(properties = {
    "pseudonymizer.retry.max-attempts=1"
  })
  @DisplayName("Minimum Retry Attempts")
  static class MinimumRetryAttemptsTest {

    @Autowired
    private PseudonymizerConfig config;

    @Test
    @DisplayName("should allow minimum retry attempts configuration")
    void shouldAllowMinimumRetryAttemptsConfiguration() {
      assertThat(config.retry().maxAttempts()).isEqualTo(1);
    }
  }

  @SpringBootTest
  @TestPropertySource(properties = {
    "pseudonymizer.read-timeout=PT1M30S"
  })
  @DisplayName("Complex Duration")
  static class ComplexDurationTest {

    @Autowired
    private PseudonymizerConfig config;

    @Test
    @DisplayName("should parse complex duration correctly")
    void shouldParseComplexDurationCorrectly() {
      assertThat(config.readTimeout()).isEqualTo(Duration.parse("PT1M30S"));
      assertThat(config.readTimeout().toSeconds()).isEqualTo(90);
    }
  }
}