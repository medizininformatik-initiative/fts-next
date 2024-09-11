package care.smith.fts.test;

import static java.util.Optional.ofNullable;

import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.auth.HttpClientAuthMethod;
import java.util.Map;
import java.util.Optional;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientSsl;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.Builder;

@Slf4j
@TestConfiguration
@ConfigurationProperties("test")
@Setter
public class TestWebClientConfig {

  private Map<String, Entry> webclient;

  private final WebClientSsl ssl;

  @Autowired
  public TestWebClientConfig(WebClientSsl ssl) {
    this.ssl = ssl;
  }

  @Override
  public String toString() {
    return "TestWebClientConfig{" + "webclient='" + webclient + '\'' + '}';
  }

  public void customize(WebClient.Builder base, String clientName) {
    findConfigurationEntry(clientName).ifPresent(e -> e.customize(base, ssl));
  }

  private Optional<Entry> findConfigurationEntry(String clientName) {
    return ofNullable(webclient).flatMap(m -> ofNullable(m.get(clientName)));
  }

  record Entry(HttpClientAuthMethod.AuthMethod auth, HttpClientConfig.Ssl ssl) {
    public void customize(Builder base, WebClientSsl wCSsl) {
      base.apply(b -> ofNullable(auth).ifPresent(a -> a.customize(b)))
          .apply(wCSsl.fromBundle(ssl.bundle()));
    }
  }
}
