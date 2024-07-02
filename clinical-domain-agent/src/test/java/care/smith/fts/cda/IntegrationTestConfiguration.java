package care.smith.fts.cda;

import java.io.IOException;
import java.net.ServerSocket;
import lombok.extern.slf4j.Slf4j;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.Builder;

@Slf4j
@TestConfiguration
public class IntegrationTestConfiguration {

  @Bean("tcaMockServer")
  MockServerClient tcaMockServer() throws IOException {
    log.info("tcaMockServer");
    return ClientAndServer.startClientAndServer(findFreePort());
  }

  @Bean("tcaWebClient")
  WebClient tcaWebClient(
      @Qualifier("tcaWebClientBuilder") WebClient.Builder builder,
      @Qualifier("tcaMockServer") MockServerClient mock) {
    String baseUrl = "http://tc-agent:%d".formatted(mock.getPort());
    WebClient client = builder.baseUrl(baseUrl).build();
    log.info("tcaWebClient: {}", client);

    return client;
  }

  @Bean("tcaWebClientBuilder")
  WebClient.Builder tcaWebClientBuilder() {
    Builder builder = WebClient.builder();
    log.info("tcaWebClientBuilder {}", builder);
    return builder;
  }

  @Bean("rdaMockServer")
  MockServerClient rdaMockServer() throws IOException {
    return ClientAndServer.startClientAndServer(findFreePort());
  }

  @Bean("rdaWebClient")
  WebClient rdaWebClient(
      WebClient.Builder builder, @Qualifier("rdaMockServer") MockServerClient mock) {
    String baseUrl = "http://rd-agent:%d".formatted(mock.getPort());
    return builder.baseUrl(baseUrl).build();
  }

  public static int findFreePort() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      socket.setReuseAddress(true);
      return socket.getLocalPort();
    }
  }
}
