package care.smith.fts.test;

import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.HttpClientConfig;
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
  private final WebClientFactory clientFactory;

  public TestWebClientFactory(TestWebClientConfig config, WebClientFactory clientFactory) {
    this.config = config;
    this.clientFactory = clientFactory;
    log.info("TestWebClientFactory {}", config);
  }

  public WebClient webClient(String baseUrl) {
    return webClient(baseUrl, "default");
  }

  public WebClient webClient(String baseUrl, String clientName) {
    return config
        .findConfigurationEntry(clientName)
        .map(c -> clientFactory.create(new HttpClientConfig(baseUrl, c.auth(), c.ssl())))
        .orElseThrow();
  }
}
