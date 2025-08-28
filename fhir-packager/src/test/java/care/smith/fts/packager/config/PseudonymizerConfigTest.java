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

  @BeforeEach
  void setUp() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  @Test
  @DisplayName("should have correct default values")
  void shouldHaveCorrectDefaultValues() {
    // Create config with defaults using the @ConstructorBinding constructor
    var config = new PseudonymizerConfig(
        "http://localhost:8080",
        Duration.parse("PT10S"),
        Duration.parse("PT60S"),
        null, // will use default RetryConfig
        true
    );
    
    assertThat(config.url()).isEqualTo("http://localhost:8080");
    assertThat(config.connectTimeout()).isEqualTo(Duration.parse("PT10S"));
    assertThat(config.readTimeout()).isEqualTo(Duration.parse("PT60S"));
    assertThat(config.retry().maxAttempts()).isEqualTo(3);
    assertThat(config.healthCheckEnabled()).isTrue();
  }

  @Test
  @DisplayName("should validate successfully with default values")
  void shouldValidateSuccessfullyWithDefaultValues() {
    var config = new PseudonymizerConfig(
        "http://localhost:8080",
        Duration.parse("PT10S"),
        Duration.parse("PT60S"),
        new PseudonymizerConfig.RetryConfig(),
        true
    );
    
    Set<ConstraintViolation<PseudonymizerConfig>> violations = validator.validate(config);
    
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("should validate successfully with custom values")
  void shouldValidateSuccessfullyWithCustomValues() {
    var retryConfig = new PseudonymizerConfig.RetryConfig(
        5, // maxAttempts
        Duration.ofSeconds(1),
        Duration.ofSeconds(30),
        2.0
    );
    
    var config = new PseudonymizerConfig(
        "https://example.com:9090",
        Duration.parse("PT15S"),
        Duration.parse("PT60S"),
        retryConfig,
        true
    );

    Set<ConstraintViolation<PseudonymizerConfig>> violations = validator.validate(config);
    
    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("should fail validation when URL is null")
  void shouldFailValidationWhenUrlIsNull() {
    var config = new PseudonymizerConfig(
        null, // null URL
        Duration.parse("PT10S"),
        Duration.parse("PT60S"),
        new PseudonymizerConfig.RetryConfig(),
        true
    );

    Set<ConstraintViolation<PseudonymizerConfig>> violations = validator.validate(config);
    
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("url");
  }

  @Test
  @DisplayName("should fail validation when connect timeout is null")
  void shouldFailValidationWhenConnectTimeoutIsNull() {
    var config = new PseudonymizerConfig(
        "http://localhost:8080",
        null, // null connect timeout
        Duration.parse("PT60S"),
        new PseudonymizerConfig.RetryConfig(),
        true
    );

    Set<ConstraintViolation<PseudonymizerConfig>> violations = validator.validate(config);
    
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("connectTimeout");
  }

  @Test
  @DisplayName("should fail validation when retry max attempts is below minimum")
  void shouldFailValidationWhenRetryMaxAttemptsIsBelowMinimum() {
    var retryConfig = new PseudonymizerConfig.RetryConfig(
        0, // below minimum of 1
        Duration.ofSeconds(1),
        Duration.ofSeconds(30),
        2.0
    );
    
    var config = new PseudonymizerConfig(
        "http://localhost:8080",
        Duration.parse("PT10S"),
        Duration.parse("PT60S"),
        retryConfig,
        true
    );

    Set<ConstraintViolation<PseudonymizerConfig>> violations = validator.validate(config);
    
    assertThat(violations).hasSize(1);
    assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("retry.maxAttempts");
  }

  @Test
  @DisplayName("should allow valid retry attempts")
  void shouldAllowValidRetryAttempts() {
    var retryConfig = new PseudonymizerConfig.RetryConfig(
        1, // minimum valid attempts
        Duration.ofSeconds(1),
        Duration.ofSeconds(30),
        2.0
    );
    
    var config = new PseudonymizerConfig(
        "http://localhost:8080",
        Duration.parse("PT10S"),
        Duration.parse("PT60S"),
        retryConfig,
        true
    );

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
      var config = new PseudonymizerConfig(
          url,
          Duration.parse("PT10S"),
          Duration.parse("PT60S"),
          new PseudonymizerConfig.RetryConfig(),
          true
      );
      
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
      var config = new PseudonymizerConfig(
          "http://localhost:8080",
          Duration.parse("PT10S"),
          timeout, // vary the read timeout
          new PseudonymizerConfig.RetryConfig(),
          true
      );
      
      Set<ConstraintViolation<PseudonymizerConfig>> violations = validator.validate(config);
      assertThat(violations).isEmpty();
    }
  }
}