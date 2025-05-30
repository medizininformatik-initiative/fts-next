package care.smith.fts.test;

import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
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
  TestWebClientConfig.class,
  WebClientFactory.class,
})
@TestComponent
public class TestWebClientFactory {

  private final TestWebClientConfig config;
  private final WebClientFactory factory;

  public TestWebClientFactory(TestWebClientConfig config, WebClientFactory factory) {
    this.config = config;
    this.factory = factory;
    log.info("TestWebClientFactory {}", config);
  }

  public WebClient webClient(String baseUrl) {
    return webClient(baseUrl, "default");
  }

  public WebClient unauthorizedWebClient(String baseUrl) {
    return webClient(baseUrl, "unauthorized");
  }

  public WebClient incorrectWebClient(String baseUrl) {
    return webClient(baseUrl, "incorrect");
  }

  public WebClient webClient(String baseUrl, String clientName) {
    return config
        .findConfigurationEntry(clientName)
        .map(c -> factory.create(new HttpClientConfig(baseUrl, c.auth(), c.ssl())))
        .orElseThrow();
  }
}
