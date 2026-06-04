package care.smith.fts.util;

import static org.assertj.core.api.Assertions.assertThat;
import static reactor.test.StepVerifier.create;

import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Pins down the connection-pool behaviour that motivated the connector swap (issue #1729). The
 * shared outbound {@link WebClient} must honour a per-pool {@code max-idle-time}: a connection that
 * has been idle longer than that is evicted rather than reused, so a request never lands on a
 * socket an upstream may have closed underneath us. The JDK connector has no per-pool idle eviction
 * (only a JVM-global keep-alive, here the {@code PT25S} default) and reuses the still-pooled
 * socket; the Reactor Netty connector evicts it and opens a fresh connection.
 */
@SpringBootTest(properties = "fts.http.client.max-idle-time=PT0.1S")
class IdleConnectionReuseTest {

  @Autowired WebClient.Builder webClientBuilder;
  @Autowired ApplicationContext applicationContext;

  @Test
  void opensFreshConnectionAfterMaxIdleTimeElapses() throws Exception {
    try (var server = new KeepAliveTestServer()) {
      WebClient client = webClientBuilder.baseUrl(server.baseUrl()).build();

      create(client.get().retrieve().bodyToMono(String.class)).expectNext("{}").verifyComplete();

      // Idle for longer than max-idle-time (100ms) while the server keeps the socket open. The
      // pool evicts on acquisition when idle time exceeds max-idle-time, so the 5x margin (not the
      // background eviction sweep) is what makes the next request deterministically open a fresh
      // connection.
      Thread.sleep(500);

      create(client.get().retrieve().bodyToMono(String.class)).expectNext("{}").verifyComplete();

      // The idle connection was evicted, so the second request opened a fresh one instead of
      // reusing the pooled socket.
      assertThat(server.connectionsAccepted()).isEqualTo(2);
    }
  }

  @Test
  void doesNotRegisterJdkHttpClientBean() {
    assertThat(applicationContext.getBeanNamesForType(HttpClient.class)).isEmpty();
  }
}
