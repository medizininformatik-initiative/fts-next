package care.smith.fts.packager.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PseudonymizerConfig}.
 * 
 * <p>Tests configuration property loading, validation, and default values.
 */
@DisplayName("PseudonymizerConfig")
class PseudonymizerConfigTest {

  private Validator validator;
  private PseudonymizerConfig config;

  @BeforeEach
  void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
    config = new PseudonymizerConfig();
  }

  @Test
  @DisplayName("should have correct default values")
  void shouldHaveCorrectDefaultValues() {
    assertThat(config.getUrl()).isEqualTo("http://localhost:8080");
    assertThat(config.getTimeout()).isEqualTo(Duration.parse("PT30S"));
    assertThat(config.getRetries()).isEqualTo(3);
  }

  @Test
  @DisplayName("should validate successfully with default values")
  void shouldValidateSuccessfullyWithDefaultValues() {
    Set<ConstraintViolation<PseudonymizerConfig>> violations = validator.validate(config);
    
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("should validate successfully with custom values")
  void shouldValidateSuccessfullyWithCustomValues() {
    config.setUrl("https://example.com:9090");
    config.setTimeout(Duration.parse("PT60S"));
    config.setRetries(5);

    Set<ConstraintViolation<PseudonymizerConfig>> violations = validator.validate(config);
    
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("should fail validation when URL is null")
  void shouldFailValidationWhenUrlIsNull() {
    config.setUrl(null);

    Set<ConstraintViolation<PseudonymizerConfig>> violations = validator.validate(config);
    
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("url");
  }

  @Test
  @DisplayName("should fail validation when timeout is null")
  void shouldFailValidationWhenTimeoutIsNull() {
    config.setTimeout(null);

    Set<ConstraintViolation<PseudonymizerConfig>> violations = validator.validate(config);
    
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("timeout");
  }

  @Test
  @DisplayName("should fail validation when retries is negative")
  void shouldFailValidationWhenRetriesIsNegative() {
    config.setRetries(-1);

    Set<ConstraintViolation<PseudonymizerConfig>> violations = validator.validate(config);
    
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("retries");
  }

  @Test
  @DisplayName("should allow zero retries")
  void shouldAllowZeroRetries() {
    config.setRetries(0);

    Set<ConstraintViolation<PseudonymizerConfig>> violations = validator.validate(config);
    
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("should accept various URL formats")
  void shouldAcceptVariousUrlFormats() {
    String[] validUrls = {
      "http://localhost:8080",
      "https://example.com",
      "https://example.com:9090",
      "http://192.168.1.100:8080/api",
      "https://subdomain.example.com/path"
    };

    for (String url : validUrls) {
      config.setUrl(url);
      Set<ConstraintViolation<PseudonymizerConfig>> violations = validator.validate(config);
      assertThat(violations).isEmpty();
    }
  }

  @Test
  @DisplayName("should accept various timeout durations")
  void shouldAcceptVariousTimeoutDurations() {
    Duration[] validTimeouts = {
      Duration.parse("PT1S"),
      Duration.parse("PT30S"),
      Duration.parse("PT1M"),
      Duration.parse("PT5M"),
      Duration.parse("PT1H")
    };

    for (Duration timeout : validTimeouts) {
      config.setTimeout(timeout);
      Set<ConstraintViolation<PseudonymizerConfig>> violations = validator.validate(config);
      assertThat(violations).isEmpty();
    }
  }
}