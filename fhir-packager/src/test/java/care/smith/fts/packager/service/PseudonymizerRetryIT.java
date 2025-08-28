package care.smith.fts.packager.service;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.packager.config.PseudonymizerConfig;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Integration test class for verifying retry logic in PseudonymizerClientImpl.
 * 
 * <p>This test focuses on verifying the retry mechanism works correctly for different
 * error scenarios without requiring a live service.
 */
class PseudonymizerRetryIT {

  /**
   * Test that verifies the retry logic categorizes errors correctly.
   * This test uses reflection to access the private isRetryable method.
   */
  @Test
  void testRetryLogicClassifiesErrorsCorrectly() throws Exception {
    // Given - Create a client with retry configuration
    var retryConfig = new PseudonymizerConfig.RetryConfig(
        3, // maxAttempts
        Duration.ofMillis(100), // initialBackoff
        Duration.ofSeconds(5), // maxBackoff
        2.0 // backoffMultiplier
    );
    
    var config = new PseudonymizerConfig(
        "http://test-pseudonymizer:8080",
        Duration.ofSeconds(10),
        Duration.ofSeconds(30),
        retryConfig,
        true
    );

    var client = new PseudonymizerClientImpl(config, WebClient.builder());

    // Use reflection to access the private isRetryable method
    var method = PseudonymizerClientImpl.class.getDeclaredMethod("isRetryable", Throwable.class);
    method.setAccessible(true);

    // Test retryable errors
    assertThat((boolean) method.invoke(client, createHttpException(HttpStatus.SERVICE_UNAVAILABLE)))
        .as("SERVICE_UNAVAILABLE should be retryable").isTrue();
        
    assertThat((boolean) method.invoke(client, createHttpException(HttpStatus.BAD_GATEWAY)))
        .as("BAD_GATEWAY should be retryable").isTrue();
        
    assertThat((boolean) method.invoke(client, createHttpException(HttpStatus.TOO_MANY_REQUESTS)))
        .as("TOO_MANY_REQUESTS should be retryable").isTrue();
        
    assertThat((boolean) method.invoke(client, createHttpException(HttpStatus.INTERNAL_SERVER_ERROR)))
        .as("INTERNAL_SERVER_ERROR should be retryable").isTrue();

    // Test non-retryable errors
    assertThat((boolean) method.invoke(client, createHttpException(HttpStatus.BAD_REQUEST)))
        .as("BAD_REQUEST should not be retryable").isFalse();
        
    assertThat((boolean) method.invoke(client, createHttpException(HttpStatus.UNAUTHORIZED)))
        .as("UNAUTHORIZED should not be retryable").isFalse();
        
    assertThat((boolean) method.invoke(client, createHttpException(HttpStatus.FORBIDDEN)))
        .as("FORBIDDEN should not be retryable").isFalse();
        
    assertThat((boolean) method.invoke(client, createHttpException(HttpStatus.NOT_FOUND)))
        .as("NOT_FOUND should not be retryable").isFalse();
  }

  /**
   * Test that verifies retry configuration is properly initialized.
   */
  @Test
  void testRetryConfigurationInitialization() {
    var retryConfig = new PseudonymizerConfig.RetryConfig(
        5, // maxAttempts
        Duration.ofSeconds(2), // initialBackoff
        Duration.ofSeconds(30), // maxBackoff
        3.0 // backoffMultiplier
    );
    
    var config = new PseudonymizerConfig(
        "http://localhost:8080",
        Duration.ofSeconds(15),
        Duration.ofSeconds(45),
        retryConfig,
        false
    );

    var client = new PseudonymizerClientImpl(config, WebClient.builder());

    // Then - Verify the configuration is accessible (this is mostly a smoke test)
    assertThat(config.retry().maxAttempts()).isEqualTo(5);
    assertThat(config.retry().initialBackoff()).isEqualTo(Duration.ofSeconds(2));
    assertThat(config.retry().maxBackoff()).isEqualTo(Duration.ofSeconds(30));
    assertThat(config.retry().backoffMultiplier()).isEqualTo(3.0);
    assertThat(config.healthCheckEnabled()).isFalse();
  }

  /**
   * Test that verifies default retry configuration works.
   */
  @Test
  void testDefaultRetryConfiguration() {
    // Given - Use default retry configuration
    var defaultRetryConfig = new PseudonymizerConfig.RetryConfig();
    
    var config = new PseudonymizerConfig(
        "http://localhost:8080",
        Duration.ofSeconds(10),
        Duration.ofSeconds(60),
        defaultRetryConfig,
        true
    );

    var client = new PseudonymizerClientImpl(config, WebClient.builder());

    // Then - Verify default values
    assertThat(config.retry().maxAttempts()).isEqualTo(3);
    assertThat(config.retry().initialBackoff()).isEqualTo(Duration.ofSeconds(1));
    assertThat(config.retry().maxBackoff()).isEqualTo(Duration.ofSeconds(30));
    assertThat(config.retry().backoffMultiplier()).isEqualTo(2.0);
  }

  /**
   * Helper method to create WebClientResponseException instances for testing.
   */
  private WebClientResponseException createHttpException(HttpStatus status) {
    return WebClientResponseException.create(
        status.value(),
        status.getReasonPhrase(),
        null,
        null,
        null
    );
  }
}