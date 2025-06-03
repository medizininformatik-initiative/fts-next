package care.smith.fts.cda.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static reactor.test.StepVerifier.create;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.cda.CohortSelector;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExternalCohortSelectorTest {

  private CohortSelector selector;

  @BeforeEach
  void setUp() {
    selector =
        new ExternalCohortSelector()
            .create(new CohortSelector.Config(), new ExternalCohortSelector.Config());
  }

  @Test
  void testConfigType() {
    assertThat(new ExternalCohortSelector().getConfigType()).isNotNull();
  }

  @Test
  void containsExactlyPatientsConfigured() {
    create(selector.selectCohort(List.of("pid-1", "pid-2")).map(ConsentedPatient::id))
        .expectNext("pid-1", "pid-2")
        .verifyComplete();
  }

  @Test
  void emptyBundleGivesEmptyResult() {
    create(selector.selectCohort(List.of())).verifyComplete();
  }
}
