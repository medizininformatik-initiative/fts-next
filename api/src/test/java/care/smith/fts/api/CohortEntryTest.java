package care.smith.fts.api;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import care.smith.fts.api.CohortEntry.Consented;
import care.smith.fts.api.CohortEntry.Excluded;
import org.junit.jupiter.api.Test;

class CohortEntryTest {

  @Test
  void consentedRequiresPatient() {
    assertThatNullPointerException().isThrownBy(() -> new Consented(null));
  }

  @Test
  void excludedRequiresIdentifier() {
    assertThatNullPointerException().isThrownBy(() -> new Excluded(null, "reason"));
  }

  @Test
  void excludedRequiresReason() {
    assertThatNullPointerException().isThrownBy(() -> new Excluded("id", null));
  }
}
