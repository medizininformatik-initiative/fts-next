package care.smith.fts.util.tca;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SecureMappingResponseTest {

  @Test
  void tidPidMapCannotBeNull() {
    assertThrows(
        NullPointerException.class, () -> new SecureMappingResponse(null, Duration.ofSeconds(1)));
  }

  @Test
  void durationCannotBeNull() {
    assertThrows(NullPointerException.class, () -> new SecureMappingResponse(Map.of(), null));
  }
}
