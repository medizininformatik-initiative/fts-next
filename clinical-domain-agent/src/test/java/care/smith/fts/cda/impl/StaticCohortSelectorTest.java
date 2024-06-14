package care.smith.fts.cda.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.cda.CohortSelector;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StaticCohortSelectorTest {

  private static final String TEST_PID = "some-091541";

  private CohortSelector selector;

  @BeforeEach
  void setUp() {
    selector =
        new StaticCohortSelector()
            .create(
                new CohortSelector.Config(), new StaticCohortSelector.Config(List.of(TEST_PID)));
  }

  @Test
  void testConfigType() {
    assertThat(new StaticCohortSelector().getConfigType()).isNotNull();
  }

  @Test
  void containsExactlyPatientsConfigured() {
    create(selector.selectCohort().map(ConsentedPatient::id)).expectNext(TEST_PID).verifyComplete();
  }
}
