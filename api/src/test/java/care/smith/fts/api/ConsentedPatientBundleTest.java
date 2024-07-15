package care.smith.fts.api;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ConsentedPatientBundleTest {
  @Test
  void nullsAllowed() {
    assertThatNoException().isThrownBy(() -> new ConsentedPatientBundle(null, null));
  }
}
