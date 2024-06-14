package care.smith.fts.tca.consent;

import care.smith.fts.api.ConsentedPatient;
import java.util.HashSet;
import java.util.List;
import reactor.core.publisher.Mono;

public interface ConsentProvider {
  Mono<List<ConsentedPatient>> consentedPatientsPage(
      String domain, HashSet<String> policies, int from, int to);
}
