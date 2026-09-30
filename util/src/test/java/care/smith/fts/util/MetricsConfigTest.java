package care.smith.fts.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class MetricsConfigTest {

  private final MeterRegistry registry = new SimpleMeterRegistry();

  MetricsConfigTest() {
    new MetricsConfig().ignoreUriQueryParamsCustomizer().customize(registry);
  }

  private String uriTag(String meterName, String uri) {
    return registry.timer(meterName, "uri", uri).getId().getTag("uri");
  }

  @Test
  void stripsQueryParamsFromClientRequestUri() {
    assertThat(uriTag("http.client.requests", "/Patient?_count=10&page=2")).isEqualTo("/Patient");
  }

  @Test
  void replacesUuidsInClientRequestUri() {
    assertThat(uriTag("http.client.requests", "/Patient/0f8fad5b-d9cb-469f-a165-70867728950e"))
        .isEqualTo("/Patient/UUID");
  }

  @Test
  void keepsUriOfOtherMeters() {
    assertThat(uriTag("http.server.requests", "/Patient?_count=10"))
        .isEqualTo("/Patient?_count=10");
  }

  @Test
  void keepsClientRequestWithoutUri() {
    assertThat(registry.timer("http.client.requests", "method", "GET").getId().getTags())
        .extracting(Tag::getKey)
        .containsExactly("method");
  }
}
