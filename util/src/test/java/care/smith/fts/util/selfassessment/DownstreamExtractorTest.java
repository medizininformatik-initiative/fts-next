package care.smith.fts.util.selfassessment;

import static care.smith.fts.util.selfassessment.DownstreamExtractor.extract;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DownstreamExtractorTest {

  @Test
  void extractsTopLevelBaseUrl() {
    var result = extract(Map.of("baseUrl", "http://x:1"));
    assertThat(result)
        .extracting("label", "baseUrl")
        .containsExactly(tuple("baseUrl", "http://x:1"));
  }

  @Test
  void extractsNestedBaseUrl() {
    var root =
        Map.of("deidentificator", Map.of("foo", Map.of("server", Map.of("baseUrl", "http://x:1"))));
    var result = extract(root);
    assertThat(result)
        .extracting("label", "baseUrl")
        .containsExactly(tuple("deidentificator.foo.server", "http://x:1"));
  }

  @Test
  void extractsMultipleBaseUrls() {
    var root =
        Map.of(
            "a", Map.of("baseUrl", "http://a"),
            "b", Map.of("nested", Map.of("baseUrl", "http://b")));
    var result = extract(root);
    assertThat(result).extracting("baseUrl").containsExactlyInAnyOrder("http://a", "http://b");
  }

  @Test
  void emptyMapReturnsEmpty() {
    assertThat(extract(Map.of())).isEmpty();
  }

  @Test
  void blankBaseUrlIgnored() {
    var result = extract(Map.of("server", Map.of("baseUrl", "")));
    assertThat(result).isEmpty();
  }

  @Test
  void walksInsideLists() {
    var root =
        Map.of("items", List.of(Map.of("baseUrl", "http://x"), Map.of("baseUrl", "http://y")));
    var result = extract(root);
    assertThat(result).extracting("baseUrl").containsExactlyInAnyOrder("http://x", "http://y");
  }

  @Test
  void ignoresNonCollectionLeaves() {
    var root = Map.of("count", 42, "server", Map.of("baseUrl", "http://x"));
    var result = extract(root);
    assertThat(result).extracting("baseUrl").containsExactly("http://x");
  }
}
