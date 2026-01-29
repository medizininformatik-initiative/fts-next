package care.smith.fts.util.tca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SecureMappingResponseTest {

  @Test
  void tidPidMapCannotBeNull() {
    assertThrows(NullPointerException.class, () -> new SecureMappingResponse(null, Map.of()));
  }

  @Test
  void dateShiftMapCannotBeNull() {
    assertThrows(NullPointerException.class, () -> new SecureMappingResponse(Map.of(), null));
  }

  @Nested
  class BuildResolveResponseTests {

    @Test
    void shouldBuildResolveResponseCorrectly() {
      var testMap =
          Map.of(
              "value1", "hash1",
              "value2", "hash2",
              "ds:2024-03-15", "2024-03-20",
              "ds:2024-01-01", "2024-01-06");

      var response = SecureMappingResponse.buildResolveResponse(testMap);

      assertThat(response.tidPidMap()).hasSize(2);
      assertThat(response.tidPidMap()).containsEntry("value1", "hash1");
      assertThat(response.tidPidMap()).containsEntry("value2", "hash2");
      assertThat(response.dateShiftMap()).hasSize(2);
      assertThat(response.dateShiftMap()).containsEntry("2024-03-15", "2024-03-20");
      assertThat(response.dateShiftMap()).containsEntry("2024-01-01", "2024-01-06");
    }

    @Test
    void shouldHandleEmptySourceMap() {
      var response = SecureMappingResponse.buildResolveResponse(new HashMap<>());

      assertThat(response.tidPidMap()).isEmpty();
      assertThat(response.dateShiftMap()).isEmpty();
    }

    @Test
    void shouldHandleOnlyDateShiftEntries() {
      var testMap = Map.of("ds:2024-03-15", "2024-03-20");

      var response = SecureMappingResponse.buildResolveResponse(testMap);

      assertThat(response.tidPidMap()).isEmpty();
      assertThat(response.dateShiftMap()).hasSize(1);
      assertThat(response.dateShiftMap()).containsEntry("2024-03-15", "2024-03-20");
    }

    @Test
    void shouldHandleOnlyTidPidEntries() {
      var testMap = Map.of("tid1", "pid1", "tid2", "pid2");

      var response = SecureMappingResponse.buildResolveResponse(testMap);

      assertThat(response.tidPidMap()).hasSize(2);
      assertThat(response.dateShiftMap()).isEmpty();
    }
  }

  @Nested
  class ConstructorTests {

    @Test
    void shouldCreateDefensiveCopyOfMaps() {
      var originalTidPidMap = new HashMap<String, String>();
      originalTidPidMap.put("key1", "value1");
      var originalDateShiftMap = new HashMap<String, String>();
      originalDateShiftMap.put("2024-01-01", "2024-01-06");

      var response = new SecureMappingResponse(originalTidPidMap, originalDateShiftMap);
      originalTidPidMap.put("key2", "value2");
      originalDateShiftMap.put("2024-02-01", "2024-02-06");

      assertThat(response.tidPidMap()).hasSize(1);
      assertThat(response.tidPidMap()).containsEntry("key1", "value1");
      assertThat(response.dateShiftMap()).hasSize(1);
      assertThat(response.dateShiftMap()).containsEntry("2024-01-01", "2024-01-06");
    }

    @Test
    void shouldExposeImmutableMaps() {
      var response = new SecureMappingResponse(Map.of("key1", "value1"), Map.of("d1", "d2"));

      assertThatThrownBy(() -> response.tidPidMap().put("key2", "value2"))
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> response.dateShiftMap().put("d3", "d4"))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }
}
