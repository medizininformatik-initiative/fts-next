package care.smith.fts.cda.impl;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.util.HTTPClientConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.jupiter.api.Test;

class TCACohortSelectorFactoryTest {

  @Test
  void testConfigType() {
    assertThat(new TCACohortSelectorFactory(HttpClientBuilder.create(), new ObjectMapper()).getConfigType()).isNotNull();
  }

  @Test
  void testCreate() {
    assertThat(
            new TCACohortSelectorFactory(HttpClientBuilder.create(), new ObjectMapper())
                .create(
                    null,
                    new TCACohortSelectorConfig(
                        new HTTPClientConfig("http://dummy.example.com"), null, null)))
        .isNotNull();
  }
}
