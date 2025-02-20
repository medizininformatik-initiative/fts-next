package care.smith.fts.rda.rest;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static reactor.test.StepVerifier.create;

import care.smith.fts.rda.ResearchDomainAgent;
import care.smith.fts.rda.TransferProcessRunner.Status;
import care.smith.fts.test.TestWebClientFactory;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound;
import org.springframework.web.reactive.function.client.WebClientResponseException.UnsupportedMediaType;
import reactor.core.publisher.Mono;

@Slf4j
@SpringBootTest(classes = ResearchDomainAgent.class, webEnvironment = RANDOM_PORT)
@Import(TestWebClientFactory.class)
public class TransferProcessControllerIT {

  private WebClient client;

  @BeforeEach
  final void setUpTransferProcessControllerIT(
      @LocalServerPort int port, @Autowired TestWebClientFactory factory) {
    client = factory.webClient("https://localhost:" + port);
  }

  @Test
  void startProcessWithInvalidProject() {
    create(
            client
                .post()
                .uri("/api/v2/process/non-existent/patient")
                .headers(h -> h.setContentType(APPLICATION_FHIR_JSON))
                .retrieve()
                .toBodilessEntity())
        .expectError(WebClientResponseException.NotFound.class)
        .verifyThenAssertThat()
        .hasOperatorErrors();
  }

  @Test
  void bodyHasWrongContentType() {
    create(client.post().uri("/api/v2/process/test/patient").retrieve().toBodilessEntity())
        .expectError(UnsupportedMediaType.class)
        .verifyThenAssertThat()
        .hasOperatorErrors();
  }

  @Test
  void bodyNotDeserializable() {
    create(
            client
                .post()
                .uri("/api/v2/process/example/patient")
                .headers(h -> h.setContentType(APPLICATION_FHIR_JSON))
                .bodyValue("{NoBundle: 0}")
                .retrieve()
                .onStatus(
                    r -> r.equals(HttpStatus.resolve(500)),
                    (c) ->
                        c.bodyToMono(ProblemDetail.class)
                            .flatMap(p -> Mono.error(new IllegalStateException(p.getDetail()))))
                .toBodilessEntity())
        .expectError(DecodingException.class)
        .verifyThenAssertThat()
        .hasOperatorErrors();
  }

  @Test
  void callingStatusWithWrongProcessIdReturns404() throws IOException {
    create(
            client
                .get()
                .uri("/api/v2/process/status/unknown-id")
                .retrieve()
                .bodyToMono(Status.class))
        .expectError(NotFound.class)
        .verifyThenAssertThat()
        .hasOperatorErrors();
  }

  @Test
  void projects() {
    create(client.get().uri("/api/v2/projects").retrieve().bodyToMono(String.class))
        .assertNext(
            transferProcessDefinitions -> {
              assertThat(transferProcessDefinitions).isEqualTo("[\"example\"]");
            })
        .verifyComplete();
  }

  @Test
  void project() {
    create(client.get().uri("/api/v2/projects/example").retrieve().bodyToMono(String.class))
        .assertNext(
            transferProcessDefinitions -> {
              assertThat(transferProcessDefinitions).contains("deidentificator");
              assertThat(transferProcessDefinitions).contains("bundleSender");
              assertThat(transferProcessDefinitions).contains("patient-102931");
            })
        .verifyComplete();
  }

  @Test
  void projectReturns404() {
    create(client.get().uri("/api/v2/projects/doesNotExist").retrieve().bodyToMono(String.class))
        .verifyErrorSatisfies(
            error -> {
              assertThat(error)
                  .asInstanceOf(type(WebClientResponseException.class))
                  .satisfies(e -> assertThat(e.getStatusCode()).isEqualTo(NOT_FOUND));
            });
  }
}
