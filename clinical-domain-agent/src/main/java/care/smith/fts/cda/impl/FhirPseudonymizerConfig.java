package care.smith.fts.cda.impl;

import care.smith.fts.util.HttpClientConfig;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Optional;

/**
 * Configuration for FHIR Pseudonymizer service integration in Clinical Domain Agent.
 *
 * <p>This configuration enables deidentification via an external FHIR Pseudonymizer service, which
 * delegates pseudonym generation to the Trust Center Agent's Vfps-compatible FHIR operations.
 *
 * <p>Configuration example:
 *
 * <pre>{@code
 * deidentificator:
 *   fhir-pseudonymizer:
 *     server:
 *       baseUrl: "https://fhir-pseudonymizer.clinical.example.com"
 *       auth:
 *         type: oauth2
 *         clientId: "cda-client"
 *         clientSecret: "${FHIR_PSEUDONYMIZER_SECRET}"
 *     timeout: 60s
 *     maxRetries: 3
 * }</pre>
 *
 * @param server HTTP client configuration for the FHIR Pseudonymizer service
 * @param timeout Request timeout (default: 60 seconds)
 * @param maxRetries Maximum retry attempts (default: 3)
 */
public record FhirPseudonymizerConfig(
    @NotNull HttpClientConfig server, Duration timeout, Integer maxRetries) {

  public FhirPseudonymizerConfig(HttpClientConfig server, Duration timeout, Integer maxRetries) {
    this.server = server;
    this.timeout = Optional.ofNullable(timeout).orElse(Duration.ofSeconds(60));
    this.maxRetries = Optional.ofNullable(maxRetries).orElse(3);

    if (this.timeout.isNegative() || this.timeout.isZero()) {
      throw new IllegalArgumentException("Timeout must be positive");
    }
    if (this.maxRetries < 0) {
      throw new IllegalArgumentException("Max retries must be non-negative");
    }
  }
}
