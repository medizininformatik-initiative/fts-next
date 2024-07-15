package care.smith.fts.tca.consent;

import java.util.Set;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

public interface ConsentProvider {
  Mono<Bundle> consentedPatientsPage(
      String domain, String policySystem, Set<String> policies, UriComponentsBuilder requestUrl);

  Mono<Bundle> consentedPatientsPage(
      String domain,
      String policySystem,
      Set<String> policies,
      UriComponentsBuilder requestUrl,
      int from,
      int count);
}
