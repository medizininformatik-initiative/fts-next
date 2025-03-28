package care.smith.fts.test;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static reactor.test.StepVerifier.create;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClientResponseException.Unauthorized;

@Slf4j
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestWebClientFactory.class)
public abstract class AbstractAuthIT {

  private WebClient client;

  private final String webclientName;

  public AbstractAuthIT() {
    this.webclientName = "default";
  }

  public AbstractAuthIT(String webclientName) {
    this.webclientName = webclientName;
  }

  protected abstract RequestHeadersSpec<?> protectedEndpoint(WebClient client);

  @BeforeEach
  final void setUpTransferProcessControllerIT(
      @LocalServerPort int port, @Autowired TestWebClientFactory factory) {
    client = factory.webClient("https://localhost:" + port, webclientName);
  }

  @Test
  void failWithWrongAuth(@LocalServerPort int port, @Autowired TestWebClientFactory factory) {
    var client = factory.unauthorizedWebClient("https://localhost:" + port);
    create(protectedEndpoint(client).retrieve().bodyToMono(String.class))
        .expectError(Unauthorized.class)
        .verify();
  }

  @Test
  void publicEndpointRequiresNoAuth(
      @LocalServerPort int port, @Autowired TestWebClientFactory factory) {
    var client = factory.unauthorizedWebClient("https://localhost:" + port);
    create(client.get().uri("/actuator/health").retrieve().bodyToMono(String.class))
        .expectNextCount(1)
        .verifyComplete();
  }

  @Test
  void authorizedClientCanAccessProtectedEndpoint() {
    create(protectedEndpoint(client).retrieve().bodyToMono(String.class))
        .expectNextCount(1)
        .verifyComplete();
  }
}
