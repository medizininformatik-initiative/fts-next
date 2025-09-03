package care.smith.fts.packager.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import care.smith.fts.packager.config.PseudonymizerConfig;
import java.time.Duration;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Test class for PseudonymizerClientImpl focusing on retry logic and error handling.
 */
@ExtendWith(MockitoExtension.class)
class PseudonymizerClientImplTest {

  @Mock
  private WebClient.Builder webClientBuilder;

  @Mock
  private WebClient webClient;

  @Mock
  private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

  @Mock
  private WebClient.RequestBodyUriSpec requestBodyUriSpec;

  @Mock
  private WebClient.ResponseSpec responseSpec;

  private PseudonymizerConfig config;
  private PseudonymizerClientImpl client;

  @BeforeEach
  void setUp() {
    // Create test configuration with retry settings
    var retryConfig = new PseudonymizerConfig.RetryConfig(
        3, // maxAttempts
        Duration.ofMillis(100), // initialBackoff
        Duration.ofSeconds(5), // maxBackoff
        2.0 // backoffMultiplier
    );
    
    config = new PseudonymizerConfig(
        "http://test-pseudonymizer:8080",
        Duration.ofSeconds(10),
        Duration.ofSeconds(30),
        retryConfig,
        true
    );

    // Mock WebClient builder chain
    when(webClientBuilder.baseUrl(any(String.class))).thenReturn(webClientBuilder);
    when(webClientBuilder.clientConnector(any())).thenReturn(webClientBuilder);
    when(webClientBuilder.defaultHeader(any(String.class), any(String.class)))
        .thenReturn(webClientBuilder);
    when(webClientBuilder.codecs(any())).thenReturn(webClientBuilder);
    when(webClientBuilder.build()).thenReturn(webClient);

    client = new PseudonymizerClientImpl(config, webClientBuilder);
  }

  @Test
  void testIsRetryable_ServiceUnavailable_ShouldRetry() {
    WebClientResponseException exception = WebClientResponseException.create(
        HttpStatus.SERVICE_UNAVAILABLE.value(),
        "Service Unavailable",
        null, null, null
    );

    boolean result = invokeIsRetryable(exception);

    assertThat(result).isTrue();
  }

  @Test
  void testIsRetryable_BadGateway_ShouldRetry() {
    WebClientResponseException exception = WebClientResponseException.create(
        HttpStatus.BAD_GATEWAY.value(),
        "Bad Gateway",
        null, null, null
    );

    boolean result = invokeIsRetryable(exception);

    assertThat(result).isTrue();
  }

  @Test
  void testIsRetryable_TooManyRequests_ShouldRetry() {
    WebClientResponseException exception = WebClientResponseException.create(
        HttpStatus.TOO_MANY_REQUESTS.value(),
        "Too Many Requests",
        null, null, null
    );

    boolean result = invokeIsRetryable(exception);

    assertThat(result).isTrue();
  }

  @Test
  void testIsRetryable_BadRequest_ShouldNotRetry() {
    WebClientResponseException exception = WebClientResponseException.create(
        HttpStatus.BAD_REQUEST.value(),
        "Bad Request",
        null, null, null
    );

    boolean result = invokeIsRetryable(exception);

    assertThat(result).isFalse();
  }

  @Test
  void testIsRetryable_Unauthorized_ShouldNotRetry() {
    WebClientResponseException exception = WebClientResponseException.create(
        HttpStatus.UNAUTHORIZED.value(),
        "Unauthorized",
        null, null, null
    );

    boolean result = invokeIsRetryable(exception);

    assertThat(result).isFalse();
  }

  @Test
  void testIsRetryable_Forbidden_ShouldNotRetry() {
    WebClientResponseException exception = WebClientResponseException.create(
        HttpStatus.FORBIDDEN.value(),
        "Forbidden",
        null, null, null
    );

    boolean result = invokeIsRetryable(exception);

    assertThat(result).isFalse();
  }

  @Test
  void testIsRetryable_NotFound_ShouldNotRetry() {
    WebClientResponseException exception = WebClientResponseException.create(
        HttpStatus.NOT_FOUND.value(),
        "Not Found",
        null, null, null
    );

    boolean result = invokeIsRetryable(exception);

    assertThat(result).isFalse();
  }

  @Test
  void testIsRetryable_InternalServerError_ShouldRetry() {
    WebClientResponseException exception = WebClientResponseException.create(
        HttpStatus.INTERNAL_SERVER_ERROR.value(),
        "Internal Server Error",
        null, null, null
    );

    boolean result = invokeIsRetryable(exception);

    assertThat(result).isTrue();
  }

  @Test
  void testHealthCheck_Success() {
    when(webClient.get()).thenReturn(requestHeadersUriSpec);
    when(requestHeadersUriSpec.uri("/fhir/metadata")).thenReturn(requestHeadersUriSpec);
    when(requestHeadersUriSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.bodyToMono(String.class)).thenReturn(Mono.just("OK"));

    StepVerifier.create(client.checkHealth())
        .expectNextMatches(status -> status.healthy() && status.responseTimeMs() >= 0)
        .verifyComplete();
  }

  @Test
  void testHealthCheck_DisabledInConfig() {
    var disabledConfig = new PseudonymizerConfig(
        "http://test:8080",
        Duration.ofSeconds(10),
        Duration.ofSeconds(30),
        new PseudonymizerConfig.RetryConfig(),
        false // healthCheckEnabled = false
    );
    
    var disabledClient = new PseudonymizerClientImpl(disabledConfig, webClientBuilder);

    StepVerifier.create(disabledClient.checkHealth())
        .expectNextMatches(status -> status.healthy() && status.responseTimeMs() == 0)
        .verifyComplete();
  }

  @Test
  void testPseudonymize_NullBundle_ShouldFail() {
    StepVerifier.create(client.pseudonymize(null))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  @Test
  void testPseudonymize_WithCustomConfig_NullBundle_ShouldFail() {
    StepVerifier.create(client.pseudonymize(null, null))
        .expectError(IllegalArgumentException.class)
        .verify();
  }

  /**
   * Helper method to test the private isRetryable method using reflection.
   * This is necessary since the method is private but contains important retry logic.
   */
  private boolean invokeIsRetryable(Throwable throwable) {
    try {
      var method = PseudonymizerClientImpl.class.getDeclaredMethod("isRetryable", Throwable.class);
      method.setAccessible(true);
      return (boolean) method.invoke(client, throwable);
    } catch (Exception e) {
      throw new RuntimeException("Failed to invoke isRetryable method", e);
    }
  }
}
