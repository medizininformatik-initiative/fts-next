package care.smith.fts.rda.impl;

import care.smith.fts.util.HttpClientConfig;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Optional;

/**
 * Configuration for FHIR Pseudonymizer service integration in Research Domain Agent.
 *
 * <p>This configuration enables deidentification via an external FHIR Pseudonymizer service, which
 * delegates pseudonym resolution to the Trust Center Agent's Vfps-compatible FHIR operations.
 *
 * <p>In the RDA context, the FHIR Pseudonymizer resolves transport IDs (tIDs) in the incoming
 * bundle to their corresponding secure pseudonyms (sIDs) via TCA's /rd-agent/fhir endpoint.
 *
 * <p>Configuration example:
 *
 * <pre>{@code
 * deidentificator:
 *   fhir-pseudonymizer:
 *     server:
 *       baseUrl: "https://fhir-pseudonymizer.research.example.com"
 *       auth:
 *         type: oauth2
 *         clientId: "rda-client"
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
