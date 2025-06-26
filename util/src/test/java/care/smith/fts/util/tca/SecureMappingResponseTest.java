package care.smith.fts.util.tca;

import static java.time.Duration.ofDays;
import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SecureMappingResponseTest {

  @Test
  void tidPidMapCannotBeNull() {
    assertThrows(NullPointerException.class, () -> new SecureMappingResponse(null, ofSeconds(1)));
  }

  @Test
  void durationCannotBeNull() {
    assertThrows(NullPointerException.class, () -> new SecureMappingResponse(Map.of(), null));
  }

  @Nested
  class BuildResolveResponseTests {

    @Test
    void shouldBuildResolveResponseCorrectly() {
      var testMap = Map.of("value1", "hash1", "value2", "hash2", "dateShiftMillis", "86400000");

      var response = SecureMappingResponse.buildResolveResponse(testMap);

      assertThat(response.tidPidMap()).hasSize(2);
      assertThat(response.tidPidMap()).containsEntry("value1", "hash1");
      assertThat(response.tidPidMap()).containsEntry("value2", "hash2");
      assertThat(response.tidPidMap()).doesNotContainKey("dateShiftMillis");
      assertThat(response.dateShiftBy()).isEqualTo(ofDays(1));
    }

    @Test
    void shouldThrowExceptionForInvalidDateShiftMillis() {
      var testMap = Map.of("dateShiftMillis", "not-a-number");

      assertThatThrownBy(() -> SecureMappingResponse.buildResolveResponse(testMap))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Invalid dateShiftMillis value: 'not-a-number'");
    }

    @Test
    void shouldThrowExceptionForMissingDateShiftMillis() {
      assertThatThrownBy(() -> SecureMappingResponse.buildResolveResponse(new HashMap<>()))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldHandleEmptyMapExceptDateShiftMillis() {
      var testMap = Map.of("dateShiftMillis", "0");

      var response = SecureMappingResponse.buildResolveResponse(testMap);

      assertThat(response.tidPidMap()).isEmpty();
      assertThat(response.dateShiftBy()).isEqualTo(Duration.ZERO);
    }

    @Test
    void shouldHandleNegativeDateShiftMillis() {
      var testMap = Map.of("dateShiftMillis", "-86400000");
      var response = SecureMappingResponse.buildResolveResponse(testMap);
      assertThat(response).isEqualTo(new SecureMappingResponse(Map.of(), ofDays(-1)));
    }
  }

  @Nested
  class ConstructorTests {

    @Test
    void shouldCreateDefensiveCopyOfMap() {
      var originalMap = new HashMap<String, String>();
      originalMap.put("key1", "value1");

      var response = new SecureMappingResponse(originalMap, ofSeconds(1));
      originalMap.put("key2", "value2");

      assertThat(response.tidPidMap()).hasSize(1);
      assertThat(response.tidPidMap()).containsEntry("key1", "value1");
      assertThat(response.tidPidMap()).doesNotContainKey("key2");
    }

    @Test
    void shouldExposeImmutableMap() {
      var originalMap = Map.of("key1", "value1");
      var response = new SecureMappingResponse(originalMap, ofSeconds(1));

      assertThatThrownBy(() -> response.tidPidMap().put("key2", "value2"))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }
}
