package care.smith.fts.test;

import static java.util.Optional.ofNullable;

import care.smith.fts.util.HttpClientConfig;
import care.smith.fts.util.auth.HttpClientAuth;
import java.util.Map;
import java.util.Optional;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;

@Slf4j
@TestConfiguration
@ConfigurationProperties("test")
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

  public record Entry(HttpClientAuth.Config auth, HttpClientConfig.Ssl ssl) {}
}
