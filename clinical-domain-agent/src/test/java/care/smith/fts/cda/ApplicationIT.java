package care.smith.fts.cda;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static reactor.test.StepVerifier.create;

import care.smith.fts.test.TestWebClientFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.UseMainMethod;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

@SpringBootTest(webEnvironment = RANDOM_PORT, useMainMethod = UseMainMethod.ALWAYS)
@Import(TestWebClientFactory.class)
public class ApplicationIT {

  @Test
  void contextLoads() {}

  @Test
  void healthEndpointAvailable(@Autowired TestWebClientFactory factory, @LocalServerPort int port) {
    var client = factory.webClient("https://localhost:" + port);

    var response =
        client
            .get()
            .uri("/actuator/health")
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(Health.class);

    create(response).expectNextMatches(health -> health.status().equals("UP")).verifyComplete();
  }

  record Health(String status) {}
}
