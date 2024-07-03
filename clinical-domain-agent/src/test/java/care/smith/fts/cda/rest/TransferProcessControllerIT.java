package care.smith.fts.cda.rest;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import care.smith.fts.cda.BaseIT;
import care.smith.fts.cda.ClinicalDomainAgent;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

@Slf4j
@SpringBootTest(classes = ClinicalDomainAgent.class, webEnvironment = RANDOM_PORT)
public class TransferProcessControllerIT extends BaseIT {
  private WebClient client;

  @BeforeEach
  void setUp(@LocalServerPort int port) {
    client = WebClient.builder().baseUrl("http://localhost:" + port).build();
  }

  @Test
  void successfulRequest() {
    tca.when(request().withMethod("POST").withPath("/api/v2/cd/consented-patients"))
        .respond(response().withStatusCode(404));

    StepVerifier.create(
            client
                .post()
                .uri("/api/v2/process/test/start")
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(e -> log.info("Result: {}", e))
                .doOnError(e -> log.info("Error", e)))
        .expectNextCount(1)
        .verifyComplete();
  }
}
