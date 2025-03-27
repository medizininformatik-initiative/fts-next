package care.smith.fts.test;

import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.auth.HttpClientAuth;
import care.smith.fts.util.auth.HttpClientBasicAuth.Config;
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

  public WebClient webClient(String baseUrl, String clientName) {

    log.debug("TestWebClientFactory baseurl: {}, clientName: {}", baseUrl, clientName);

    return config
        .findConfigurationEntry(clientName)
        .map(c -> factory.create(new HttpClientConfig(baseUrl, c.auth(), c.ssl())))
        .orElseThrow();
  }

  public WebClient unauthorizedWebClient(String baseUrl) {
    return config
        .findConfigurationEntry("default")
        .map(
            c ->
                factory.create(
                    new HttpClientConfig(
                        baseUrl,
                        new HttpClientAuth.Config(new Config("user", "wrong password")),
                        c.ssl())))
        .orElseThrow();
  }
}
