package care.smith.fts.api;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TransportBundleTest {
  @Test
  void nullsAllowed() {
    assertThatNoException().isThrownBy(() -> new TransportBundle(null, null));
  }
}
