package care.smith.fts.test;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.ssl.SslAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.ClientHttpConnectorAutoConfiguration;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.web.reactive.function.client.WebClient;

@Slf4j
@Import({
  SslAutoConfiguration.class,
  ClientHttpConnectorAutoConfiguration.class,
  WebClientAutoConfiguration.class,
  TestWebClientConfig.class
})
@TestComponent
public class TestWebClientFactory {

  private final TestWebClientConfig config;
  private final WebClient.Builder base;

  public TestWebClientFactory(TestWebClientConfig config, WebClient.Builder base) {
    this.config = config;
    this.base = base;
    log.info("TestWebClientFactory {}", config);
  }

  public WebClient.Builder webClient() {
    return webClient("default");
  }

  public WebClient.Builder webClient(String clientName) {
    var builder = base.clone();
    config.customize(builder, clientName);
    return builder;
  }
}
