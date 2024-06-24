package care.smith.fts.cda.impl;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.util.HTTPClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest
class TCACohortSelectorFactoryTest {

  @Autowired WebClient.Builder client;

  @Test
  void testConfigType() {
    assertThat(new TCACohortSelectorFactory(client).getConfigType()).isNotNull();
  }

  @Test
  void testCreate() {
    assertThat(
            new TCACohortSelectorFactory(client)
                .create(
                    null,
                    new TCACohortSelectorConfig(
                        new HTTPClientConfig("http://dummy.example.com"),
                        null,
                        null,
                        null,
                        null)))
        .isNotNull();
  }
}
