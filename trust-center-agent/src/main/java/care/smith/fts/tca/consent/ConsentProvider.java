package care.smith.fts.tca.consent;

import java.util.HashSet;
import org.hl7.fhir.r4.model.Bundle;
import reactor.core.publisher.Mono;

public interface ConsentProvider {
  Mono<Bundle> consentedPatientsPage(
      String domain, String policySystem, HashSet<String> policies, String requestUrl);

  Mono<Bundle> consentedPatientsPage(
      String domain,
      String policySystem,
      HashSet<String> policies,
      String requestUrl,
      int from,
      int count);
}
