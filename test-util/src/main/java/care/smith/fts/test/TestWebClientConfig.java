package care.smith.fts.test;

import static java.util.Optional.ofNullable;

import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.WebClientFactory;
import care.smith.fts.util.auth.HttpClientAuth.Config;
import java.util.Map;
import java.util.Optional;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@Slf4j
@TestConfiguration
@ConfigurationProperties("test")
@Import(WebClientFactory.class)
@Setter
public class TestWebClientConfig {

  private Map<String, Entry> webclient;

  @Override
  public String toString() {
    return "TestWebClientConfig{" + "webclient='" + webclient + '\'' + '}';
  }

  public Optional<Entry> findConfigurationEntry(String clientName) {
    return ofNullable(webclient).flatMap(m -> ofNullable(m.get(clientName)));
  }

  public record Entry(Config auth, HttpClientConfig.Ssl ssl) {}
}
