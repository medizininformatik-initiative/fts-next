package care.smith.fts.tca.consent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class PolicyHandlerTest {

  private final Set<String> defaultPolicies = Set.of("a", "b");

  @Test
  void getPoliciesToCheck() {
    PolicyHandler policyHandler = new PolicyHandler(defaultPolicies);
    assertThat(policyHandler.getPoliciesToCheck(Set.of("a"))).containsExactlyInAnyOrder("a");
    assertThat(policyHandler.getPoliciesToCheck(Set.of("c"))).containsExactlyInAnyOrder("c");
    assertThat(policyHandler.getPoliciesToCheck(Set.of())).containsExactlyInAnyOrder("a", "b");
  }
}
