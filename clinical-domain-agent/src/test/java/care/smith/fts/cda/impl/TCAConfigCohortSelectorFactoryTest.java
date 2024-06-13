package care.smith.fts.cda.impl;

import static org.assertj.core.api.Assertions.assertThat;

import care.smith.fts.util.HTTPClientConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.reactive.function.client.WebClient;

@SpringBootTest
class TCAConfigCohortSelectorFactoryTest {

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
                        new HTTPClientConfig("http://dummy.example.com"), null, null)))
        .isNotNull();
  }
}
