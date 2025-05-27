package care.smith.fts.test;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static reactor.test.StepVerifier.create;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.DisabledIf;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClient.RequestHeadersSpec;
import org.springframework.web.reactive.function.client.WebClientResponseException.Unauthorized;

@Slf4j
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Import(TestWebClientFactory.class)
public abstract class AbstractAuthIT {

  private final String defaultWebClientName;

  public AbstractAuthIT(String defaultWebClientName) {
    this.defaultWebClientName = defaultWebClientName;
  }

  public AbstractAuthIT() {
    this("default");
  }

  protected abstract RequestHeadersSpec<?> protectedEndpoint(WebClient client);

  @LocalServerPort int port;
  @Autowired TestWebClientFactory factory;

  @Test
  void publicEndpoint_succeedsWithAuthentication() {
    var authorized = factory.webClient("https://localhost:" + port, defaultWebClientName);
    create(authorized.get().uri("/actuator/health").retrieve().bodyToMono(String.class))
        .expectNextCount(1)
        .verifyComplete();
  }

  @Test
  void publicEndpoint_succeedsWithoutAuthentication() {
    var unauthorized = factory.unauthorizedWebClient("https://localhost:" + port);
    create(unauthorized.get().uri("/actuator/health").retrieve().bodyToMono(String.class))
        .expectNextCount(1)
        .verifyComplete();
  }

  @Test
  @DisabledIf(
      value = "#{environment.matchesProfiles('auth_cert')}",
      reason = "Doesn't apply to Cert Auth",
      loadContext = true)
  void publicEndpoint_failsWithIncorrectAuthentication() {
    var incorrect = factory.incorrectWebClient("https://localhost:" + port);
    create(incorrect.get().uri("/actuator/health").retrieve().bodyToMono(String.class))
        .expectError(Unauthorized.class)
        .verify();
  }

  @Test
  void protectedEndpoint_succeedsWithAuthentication() {
    var authorized = factory.webClient("https://localhost:" + port, defaultWebClientName);
    create(protectedEndpoint(authorized).retrieve().bodyToMono(String.class))
        .expectNextCount(1)
        .verifyComplete();
  }

  @Test
  void protectedEndpoint_failsWithoutAuthentication() {
    var unauthorized = factory.unauthorizedWebClient("https://localhost:" + port);
    create(protectedEndpoint(unauthorized).retrieve().bodyToMono(String.class))
        .expectError(Unauthorized.class)
        .verify();
  }

  @Test
  void protectedEndpoint_failsWithIncorrectAuthentication() {
    var incorrect = factory.incorrectWebClient("https://localhost:" + port);
    create(protectedEndpoint(incorrect).retrieve().bodyToMono(String.class))
        .expectError(Unauthorized.class)
        .verify();
  }
}
