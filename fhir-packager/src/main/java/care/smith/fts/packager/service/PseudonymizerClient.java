package care.smith.fts.packager.service;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;
import reactor.core.publisher.Mono;

public interface PseudonymizerClient {

  Mono<Bundle> pseudonymize(Bundle bundle);

  Mono<Bundle> pseudonymize(Bundle bundle, Parameters customConfig);

  Mono<HealthStatus> checkHealth();

  record HealthStatus(boolean healthy, String message, long responseTimeMs) {

    public static HealthStatus healthy(long responseTimeMs) {
      return new HealthStatus(true, "Service is healthy", responseTimeMs);
    }

    public static HealthStatus unhealthy(String message) {
      return new HealthStatus(false, message, -1);
    }
  }
}