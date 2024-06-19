package care.smith.fts.tca.consent;

import jakarta.validation.constraints.NotNull;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PolicyHandler {
  final Set<String> defaultPolicies;

  @Autowired
  public PolicyHandler(Set<String> defaultPolicies) {
    this.defaultPolicies = defaultPolicies;
  }

  /***
   *
   * @param policies The Policies to check
   * @return set of policies which should be checked in gICS
   */
  Set<String> getPoliciesToCheck(@NotNull Set<String> policies) {
    if (policies.isEmpty()) {
      return defaultPolicies;
    }
    return policies;
  }
}
