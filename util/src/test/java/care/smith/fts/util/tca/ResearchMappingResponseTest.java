package care.smith.fts.util.tca;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ResearchMappingResponseTest {

  @Test
  void tidPidMapCannotBeNull() {
    assertThrows(NullPointerException.class, () -> new ResearchMappingResponse(null, null));
  }

  @Test
  void durationCannotBeNull() {
    assertThrows(NullPointerException.class, () -> new ResearchMappingResponse(Map.of(), null));
  }
}
