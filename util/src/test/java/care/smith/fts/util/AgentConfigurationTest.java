package care.smith.fts.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgentConfigurationTest {

  private static final String KEEPALIVE_PROP = "jdk.httpclient.keepalive.timeout";

  private String savedKeepalive;

  @BeforeEach
  void snapshot() {
    savedKeepalive = System.getProperty(KEEPALIVE_PROP);
    System.clearProperty(KEEPALIVE_PROP);
  }

  @AfterEach
  void restore() {
    if (savedKeepalive == null) {
      System.clearProperty(KEEPALIVE_PROP);
    } else {
      System.setProperty(KEEPALIVE_PROP, savedKeepalive);
    }
  }

  @Test
  void httpClientSetsKeepaliveSystemProperty() {
    var client = new AgentConfiguration().httpClient(Duration.ofSeconds(17));

    assertThat(client).isNotNull();
    assertThat(System.getProperty(KEEPALIVE_PROP)).isEqualTo("17");
  }

  @Test
  void httpClientRoundsToSecondsForSubSecondDurations() {
    new AgentConfiguration().httpClient(Duration.ofMillis(1500));

    assertThat(System.getProperty(KEEPALIVE_PROP)).isEqualTo("1");
  }
}
