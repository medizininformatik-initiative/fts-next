package care.smith.fts.cda.rest;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import care.smith.fts.cda.ClinicalDomainAgent;
import care.smith.fts.cda.IntegrationTestConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

@Slf4j
@SpringBootTest(
    classes = {ClinicalDomainAgent.class, IntegrationTestConfiguration.class},
    webEnvironment = RANDOM_PORT)
@TestPropertySource(properties = {"spring.config.location=src/test/resources/application.yaml"})
public class TransferProcessControllerIT {

  @Autowired
  @Qualifier("tcaMockServer")
  MockServerClient tcaMockServer;

  @Autowired
  @Qualifier("tcaWebClient")
  WebClient tcaWebClient;

  @Autowired
  @Qualifier("tcaWebClientBuilder")
  WebClient.Builder tcaWebClientBuilder;

  @Autowired
  @Qualifier("rdaMockServer")
  MockServerClient rdaMockServer;

  @Autowired
  @Qualifier("rdaWebClient")
  WebClient rdaWebClient;

  private WebClient client;

  @BeforeEach
  void setUp(@LocalServerPort int port) {
    client = WebClient.builder().baseUrl("http://localhost:" + port).build();
  }

  @Test
  void successfulRequest() {
    tcaMockServer
        .when(request().withMethod("POST").withPath("/api/v2/cd/consented-patients"))
        .respond(response().withStatusCode(404));

    log.info("tcaMockServer port: {}", tcaMockServer.getPort());
    log.info("tcaMockServer contextPath: {}", tcaMockServer.contextPath());

    StepVerifier.create(
            client
                .post()
                .uri("/api/v2/process/example-it/start")
                .retrieve()
                .bodyToMono(String.class)
                .doOnNext(e -> log.info("Result: {}", e))
                .doOnError(e -> log.info("Error", e)))
        .expectNextCount(1)
        .verifyComplete();
  }
}
