package care.smith.fts.rda.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static reactor.test.StepVerifier.create;

import care.smith.fts.rda.ResearchDomainAgent;
import care.smith.fts.test.TestWebClientFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException.Unauthorized;

@Slf4j
@SpringBootTest(classes = ResearchDomainAgent.class, webEnvironment = RANDOM_PORT)
@Import(TestWebClientFactory.class)
@ActiveProfiles("auth:cert")
public class CertAuthIT {

  private WebClient client;

  @BeforeEach
  final void setUpTransferProcessControllerIT(
      @LocalServerPort int port, @Autowired TestWebClientFactory factory) {
    client = factory.webClient("https://localhost:" + port);
  }

  @Test
  void failWithWrongAuth(@LocalServerPort int port, @Autowired TestWebClientFactory factory) {
    var client = factory.unauthorizedWebClient("https://localhost:" + port);
    create(client.get().uri("/api/v2/projects").retrieve().bodyToMono(String.class))
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
    create(client.get().uri("/api/v2/projects").retrieve().bodyToMono(String.class))
        .assertNext(
            transferProcessDefinitions -> {
              assertThat(transferProcessDefinitions).isEqualTo("[\"example\"]");
            })
        .verifyComplete();
  }
}
