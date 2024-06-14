package care.smith.fts.tca.consent;

import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PolicyHandler {
  final HashSet<String> defaultPolicies;

  @Autowired
  public PolicyHandler(HashSet<String> defaultPolicies) {
    this.defaultPolicies = defaultPolicies;
  }

  /***
   *
   * @param policies The Policies to check
   * @return set of policies which should be checked in gICS
   */
  HashSet<String> getPoliciesToCheck(@NotNull HashSet<String> policies) {
    if (policies.isEmpty()) {
      return defaultPolicies;
    }
    return policies;
  }
}
