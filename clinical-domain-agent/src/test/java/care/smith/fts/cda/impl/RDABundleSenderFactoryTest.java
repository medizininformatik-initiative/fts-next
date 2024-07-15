package care.smith.fts.cda.impl;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.util.HTTPClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest
class RDABundleSenderFactoryTest {

  private final WebClient.Builder clientBuilder = WebClient.builder();

  @Test
  void testConfigType() {
    assertThat(new RDABundleSenderFactory(clientBuilder).getConfigType()).isNotNull();
  }

  @Test
  void testCreate() {
    assertThat(
            new RDABundleSenderFactory(clientBuilder)
                .create(
                    null,
                    new RDABundleSenderConfig(new HTTPClientConfig("http://localhost"), "example")))
        .isNotNull();
  }
}
