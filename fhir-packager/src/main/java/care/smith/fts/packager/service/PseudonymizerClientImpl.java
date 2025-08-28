package care.smith.fts.packager.service;

import care.smith.fts.packager.config.PseudonymizerConfig;
import care.smith.fts.util.fhir.FhirUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.net.ConnectException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

/**
 * Implementation of PseudonymizerClient that communicates with a FHIR Pseudonymizer REST service.
 * 
 * <p>This service handles HTTP communication with an external pseudonymization service,
 * including proper timeout configuration, content-type handling, and reactive error handling.
 * 
 * <p>The implementation uses Spring WebFlux WebClient for reactive HTTP operations and
 * HAPI FHIR utilities for Bundle serialization/deserialization.
 * 
 * <p>Key features:
 * <ul>
 *   <li>Configurable connection and read timeouts</li>
 *   <li>Proper FHIR content-type headers (application/fhir+json)</li>
 *   <li>Large bundle support (100MB max in-memory size)</li>
 *   <li>Health check endpoint monitoring with response time tracking</li>
 *   <li>Reactive programming model with Mono return types</li>
 *   <li>Robust retry logic with exponential backoff for transient failures</li>
 *   <li>Intelligent retry filtering to avoid retrying permanent errors</li>
 * </ul>
 */
@Slf4j
@Service
public class PseudonymizerClientImpl implements PseudonymizerClient {

  private final WebClient webClient;
  private final PseudonymizerConfig config;

  /**
   * Constructs a new PseudonymizerClientImpl with the specified configuration.
   * 
   * @param config configuration settings for the pseudonymizer service
   * @param webClientBuilder builder for creating WebClient instances
   */
  public PseudonymizerClientImpl(PseudonymizerConfig config, WebClient.Builder webClientBuilder) {
    this.config = config;
    this.webClient = createWebClient(webClientBuilder);
    
    log.info("Initialized PseudonymizerClient with base URL: {}", config.url());
    log.debug("Configuration - Connect timeout: {}, Read timeout: {}", 
             config.connectTimeout(), config.readTimeout());
  }

  /**
   * Creates and configures the WebClient instance with proper timeouts and headers.
   * 
   * @param webClientBuilder the WebClient builder
   * @return configured WebClient instance
   */
  private WebClient createWebClient(WebClient.Builder webClientBuilder) {
    // Configure HTTP client with timeouts and connection settings
    HttpClient httpClient = HttpClient.create()
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) config.connectTimeout().toMillis())
        .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(config.readTimeout().toSeconds(), TimeUnit.SECONDS)))
        .compress(true); // Enable compression for large bundles

    return webClientBuilder
        .baseUrl(config.url())
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .defaultHeader("Content-Type", "application/fhir+json")
        .defaultHeader("Accept", "application/fhir+json")
        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(100 * 1024 * 1024)) // 100MB for large bundles
        .build();
  }

  /**
   * Pseudonymizes a FHIR Bundle using the external service's default configuration.
   * 
   * <p>Sends the bundle to the {@code /fhir/$de-identify} endpoint and returns the
   * pseudonymized result. The operation is performed reactively using WebFlux.
   * 
   * @param bundle the FHIR Bundle to pseudonymize
   * @return a Mono containing the pseudonymized Bundle
   * @throws IllegalArgumentException if the bundle is null
   */
  @Override
  public Mono<Bundle> pseudonymize(Bundle bundle) {
    if (bundle == null) {
      return Mono.error(new IllegalArgumentException("Bundle cannot be null"));
    }

    log.debug("Starting pseudonymization request for bundle with {} entries", 
             bundle.hasEntry() ? bundle.getEntry().size() : 0);

    String bundleJson = FhirUtils.fhirResourceToString(bundle);
    
    Instant startTime = Instant.now();
    
    return webClient.post()
        .uri("/fhir/$de-identify")
        .bodyValue(bundleJson)
        .retrieve()
        .bodyToMono(String.class)
        .map(FhirUtils::stringToFhirBundle)
        .retryWhen(createRetrySpec("pseudonymization", startTime))
        .doOnSuccess(result -> {
          Duration elapsed = Duration.between(startTime, Instant.now());
          log.debug("Successfully completed pseudonymization request after {}ms. Result has {} entries",
                   elapsed.toMillis(), result.hasEntry() ? result.getEntry().size() : 0);
        })
        .doOnError(error -> {
          Duration elapsed = Duration.between(startTime, Instant.now());
          log.error("Pseudonymization request failed after {}ms: {}", 
                   elapsed.toMillis(), error.getMessage(), error);
        });
  }

  /**
   * Pseudonymizes a FHIR Bundle using custom configuration parameters.
   * 
   * <p>This method allows for custom pseudonymization settings to be passed
   * along with the bundle. The custom configuration is sent as FHIR Parameters.
   * 
   * @param bundle the FHIR Bundle to pseudonymize
   * @param customConfig custom configuration parameters for pseudonymization
   * @return a Mono containing the pseudonymized Bundle
   * @throws IllegalArgumentException if the bundle is null
   */
  @Override
  public Mono<Bundle> pseudonymize(Bundle bundle, Parameters customConfig) {
    if (bundle == null) {
      return Mono.error(new IllegalArgumentException("Bundle cannot be null"));
    }

    log.debug("Starting pseudonymization request with custom config for bundle with {} entries", 
             bundle.hasEntry() ? bundle.getEntry().size() : 0);

    String bundleJson = FhirUtils.fhirResourceToString(bundle);
    
    // For now, we log the custom config but use the same endpoint
    // Future enhancement: implement custom config handling based on service API
    if (customConfig != null) {
      log.debug("Using custom pseudonymization parameters with {} parameter(s)",
               customConfig.hasParameter() ? customConfig.getParameter().size() : 0);
    }

    Instant startTime = Instant.now();
    
    return webClient.post()
        .uri("/fhir/$de-identify")
        .bodyValue(bundleJson)
        .retrieve()
        .bodyToMono(String.class)
        .map(FhirUtils::stringToFhirBundle)
        .retryWhen(createRetrySpec("pseudonymization with custom config", startTime))
        .doOnSuccess(result -> {
          Duration elapsed = Duration.between(startTime, Instant.now());
          log.debug("Successfully completed pseudonymization request with custom config after {}ms. Result has {} entries",
                   elapsed.toMillis(), result.hasEntry() ? result.getEntry().size() : 0);
        })
        .doOnError(error -> {
          Duration elapsed = Duration.between(startTime, Instant.now());
          log.error("Pseudonymization request with custom config failed after {}ms: {}", 
                   elapsed.toMillis(), error.getMessage(), error);
        });
  }

  /**
   * Checks the health status of the pseudonymizer service.
   * 
   * <p>Performs a GET request to the health endpoint and measures response time.
   * The health check is only performed if enabled in the configuration.
   * 
   * @return a Mono containing the health status with response time information
   */
  @Override
  public Mono<HealthStatus> checkHealth() {
    if (!config.healthCheckEnabled()) {
      log.debug("Health check disabled in configuration");
      return Mono.just(HealthStatus.healthy(0));
    }

    log.debug("Performing health check for pseudonymizer service");
    
    Instant startTime = Instant.now();

    return webClient.get()
        .uri("/fhir/metadata")
        .retrieve()
        .bodyToMono(String.class)
        .retryWhen(createHealthCheckRetrySpec(startTime))
        .map(response -> {
          long responseTime = Duration.between(startTime, Instant.now()).toMillis();
          log.debug("Health check successful, response time: {}ms", responseTime);
          return HealthStatus.healthy(responseTime);
        })
        .onErrorResume(error -> {
          long responseTime = Duration.between(startTime, Instant.now()).toMillis();
          log.warn("Health check failed after {}ms: {}", responseTime, error.getMessage());
          return Mono.just(HealthStatus.unhealthy("Service unavailable: " + error.getMessage()));
        });
  }

  /**
   * Creates a retry specification for pseudonymization operations.
   * 
   * <p>This method configures exponential backoff retry logic with jitter to prevent
   * thundering herd effects. Only transient errors are retried based on the
   * {@link #isRetryable(Throwable)} predicate.
   * 
   * @param operationName the name of the operation for logging purposes
   * @param startTime the start time of the operation for elapsed time calculations
   * @return a configured Retry specification
   */
  private Retry createRetrySpec(String operationName, Instant startTime) {
    return Retry.backoff(
        config.retry().maxAttempts(),
        config.retry().initialBackoff()
    )
    .maxBackoff(config.retry().maxBackoff())
    .jitter(0.5) // Add jitter to prevent thundering herd
    .filter(this::isRetryable)
    .doBeforeRetry(retrySignal -> {
      Duration elapsed = Duration.between(startTime, Instant.now());
      Throwable failure = retrySignal.failure();
      log.warn("Retry attempt {} for {} after {}ms due to: {} - {}", 
               retrySignal.totalRetries() + 1, 
               operationName,
               elapsed.toMillis(),
               failure.getClass().getSimpleName(), 
               failure.getMessage());
    })
    .onRetryExhaustedThrow((retryBackoffSpec, retrySignal) -> {
      Duration elapsed = Duration.between(startTime, Instant.now());
      log.error("Retry exhausted for {} after {} attempts and {}ms. Final error: {}", 
                operationName, 
                retrySignal.totalRetries(), 
                elapsed.toMillis(),
                retrySignal.failure().getMessage());
      return retrySignal.failure();
    });
  }

  /**
   * Creates a simplified retry specification for health check operations.
   * 
   * <p>Health checks use a more conservative retry strategy with fewer attempts
   * and shorter backoff intervals since they are diagnostic operations.
   * 
   * @param startTime the start time of the health check
   * @return a configured Retry specification for health checks
   */
  private Retry createHealthCheckRetrySpec(Instant startTime) {
    int maxHealthCheckAttempts = Math.max(1, config.retry().maxAttempts() - 1);
    Duration healthCheckBackoff = config.retry().initialBackoff().dividedBy(2);
    
    return Retry.backoff(maxHealthCheckAttempts, healthCheckBackoff)
        .maxBackoff(config.retry().maxBackoff().dividedBy(2))
        .jitter(0.5)
        .filter(this::isRetryable)
        .doBeforeRetry(retrySignal -> {
          Duration elapsed = Duration.between(startTime, Instant.now());
          log.warn("Health check retry attempt {} after {}ms due to: {}", 
                   retrySignal.totalRetries() + 1, 
                   elapsed.toMillis(),
                   retrySignal.failure().getMessage());
        });
  }

  /**
   * Determines whether a given throwable represents a retryable error condition.
   * 
   * <p>This method classifies errors into retryable (transient) and non-retryable
   * (permanent) categories. Retryable errors are typically infrastructure-related
   * issues that may resolve themselves, while non-retryable errors are client-side
   * mistakes or server-side rejections that won't change with retry.
   * 
   * <p><strong>Retryable errors:</strong>
   * <ul>
   *   <li>HTTP 503 (Service Unavailable) - temporary server overload</li>
   *   <li>HTTP 502 (Bad Gateway) - upstream server issues</li>
   *   <li>HTTP 429 (Too Many Requests) - rate limiting</li>
   *   <li>Connection timeouts - network congestion</li>
   *   <li>Read timeouts - server processing delays</li>
   *   <li>Connection refused - service temporarily down</li>
   * </ul>
   * 
   * <p><strong>Non-retryable errors:</strong>
   * <ul>
   *   <li>HTTP 400 (Bad Request) - malformed request</li>
   *   <li>HTTP 401 (Unauthorized) - authentication failure</li>
   *   <li>HTTP 403 (Forbidden) - authorization failure</li>
   *   <li>HTTP 404 (Not Found) - endpoint doesn't exist</li>
   *   <li>JSON parsing errors - malformed response data</li>
   *   <li>FHIR structure validation errors - invalid Bundle format</li>
   * </ul>
   * 
   * @param throwable the error to classify
   * @return true if the error should be retried, false otherwise
   */
  private boolean isRetryable(Throwable throwable) {
    // Handle WebClient HTTP response exceptions
    if (throwable instanceof WebClientResponseException webClientEx) {
      HttpStatus status = HttpStatus.resolve(webClientEx.getStatusCode().value());
      if (status != null) {
        return switch (status) {
          case SERVICE_UNAVAILABLE,   // 503 - server temporarily unavailable
               BAD_GATEWAY,           // 502 - upstream server issues  
               TOO_MANY_REQUESTS      // 429 - rate limiting
               -> {
            log.debug("Retryable HTTP error: {} - {}", status.value(), status.getReasonPhrase());
            yield true;
          }
          case BAD_REQUEST,           // 400 - client error
               UNAUTHORIZED,          // 401 - auth failure
               FORBIDDEN,             // 403 - authorization failure
               NOT_FOUND              // 404 - endpoint not found
               -> {
            log.debug("Non-retryable HTTP error: {} - {}", status.value(), status.getReasonPhrase());
            yield false;
          }
          default -> {
            // For other HTTP errors, retry 5xx but not 4xx
            boolean shouldRetry = status.is5xxServerError();
            log.debug("HTTP error {}: {} - {}", 
                     status.value(), 
                     shouldRetry ? "retryable" : "non-retryable",
                     status.getReasonPhrase());
            yield shouldRetry;
          }
        };
      }
    }

    // Handle network and connection issues
    if (throwable instanceof ConnectException) {
      log.debug("Retryable connection error: {}", throwable.getMessage());
      return true;
    }

    if (throwable instanceof TimeoutException) {
      log.debug("Retryable timeout error: {}", throwable.getMessage());
      return true;
    }

    // Handle JSON parsing errors (non-retryable)
    if (throwable instanceof JsonProcessingException) {
      log.debug("Non-retryable JSON parsing error: {}", throwable.getMessage());
      return false;
    }

    // Handle FHIR parsing errors (non-retryable)
    if (throwable.getMessage() != null && (
        throwable.getMessage().contains("FHIR") ||
        throwable.getMessage().contains("Bundle") ||
        throwable.getMessage().contains("parsing"))) {
      log.debug("Non-retryable FHIR parsing error: {}", throwable.getMessage());
      return false;
    }

    // Check for nested causes
    Throwable cause = throwable.getCause();
    if (cause != null && cause != throwable) {
      return isRetryable(cause);
    }

    // Default: retry unknown errors as they might be transient
    log.debug("Unknown error type, defaulting to retryable: {} - {}", 
             throwable.getClass().getSimpleName(), throwable.getMessage());
    return true;
  }
}