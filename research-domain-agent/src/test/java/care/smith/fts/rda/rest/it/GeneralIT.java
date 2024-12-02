package care.smith.fts.rda.rest.it;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import care.smith.fts.rda.TransferProcessRunner.Status;
import care.smith.fts.test.FhirGenerators;
import java.io.IOException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClientResponseException.NotFound;
import org.springframework.web.reactive.function.client.WebClientResponseException.UnsupportedMediaType;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@Slf4j
public class GeneralIT extends TransferProcessControllerIT {

  @Test
  void successfulPatientTransfer() throws IOException {
    mockDeidentifier.success();
    mockBundleSender.success();

    var transportBundle = FhirGenerators.transportBundle().generateResource();

    log.info("Start process with transport bundle of size {}", transportBundle.getEntry().size());

    startProcess(Duration.ofSeconds(3), transportBundle)
        .assertNext(r -> completeWithResources(r, 366, 1))
        .verifyComplete();
  }

  @Test
  void startProcessWithInvalidProject() {
    StepVerifier.create(
            client
                .post()
                .uri("/api/v2/process/non-existent/patient")
                .headers(h -> h.setContentType(APPLICATION_FHIR_JSON))
                .retrieve()
                .onStatus(
                    r -> r.equals(HttpStatus.resolve(404)),
                    (c) ->
                        c.bodyToMono(ProblemDetail.class)
                            .flatMap(p -> Mono.error(new IllegalStateException(p.getDetail()))))
                .toBodilessEntity())
        .expectErrorMessage("Project 'non-existent' could not be found")
        .verifyThenAssertThat()
        .hasOperatorErrors();
  }

  @Test
  void bodyHasWrongContentType() {
    StepVerifier.create(
            client.post().uri("/api/v2/process/test/patient").retrieve().toBodilessEntity())
        .expectError(UnsupportedMediaType.class)
        .verifyThenAssertThat()
        .hasOperatorErrors();
  }

  @Test
  void bodyNotDeserializable() {
    StepVerifier.create(
            client
                .post()
                .uri("/api/v2/process/test/patient")
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
    mockDeidentifier.success();
    mockBundleSender.success();

    var transportBundle = FhirGenerators.transportBundle().generateResource();

    client
        .post()
        .uri("/api/v2/process/test/patient")
        .headers(h -> h.setContentType(APPLICATION_FHIR_JSON))
        .bodyValue(transportBundle)
        .retrieve()
        .toBodilessEntity()
        .mapNotNull(r -> r.getHeaders().get("Content-Location"))
        .flatMap(
            r -> {
              var uri = r.getFirst().concat("-unknown-process-id");
              return client.get().uri(uri).retrieve().bodyToMono(Status.class);
            })
        .as(
            response ->
                StepVerifier.create(response)
                    .expectError(NotFound.class)
                    .verifyThenAssertThat()
                    .hasOperatorErrors());
  }

  @Test
  void callingStatusReturnsQueued() throws IOException {
    mockDeidentifier.success();
    mockBundleSender.success();

    var transportBundle = FhirGenerators.transportBundle().generateResource();

    client
        .post()
        .uri("/api/v2/process/test/patient")
        .headers(h -> h.setContentType(APPLICATION_FHIR_JSON))
        .bodyValue(transportBundle)
        .retrieve()
        .toBodilessEntity()
        .mapNotNull(r -> r.getHeaders().get("Content-Location"))
        .flatMap(
            r -> {
              var uri = r.getFirst().concat("-unknown-process-id");
              return client.get().uri(uri).retrieve().bodyToMono(Status.class);
            })
        .as(
            response ->
                StepVerifier.create(response)
                    .expectError(NotFound.class)
                    .verifyThenAssertThat()
                    .hasOperatorErrors());
  }

  @Test
  void projects() {
    StepVerifier.create(client.get().uri("/api/v2/projects").retrieve().bodyToMono(String.class))
        .assertNext(
            transferProcessDefinitions -> {
              assertThat(transferProcessDefinitions).isEqualTo("[\"test\"]");
            })
        .verifyComplete();
  }

  @Test
  void project() {
    StepVerifier.create(
            client.get().uri("/api/v2/projects/test").retrieve().bodyToMono(String.class))
        .assertNext(
            transferProcessDefinitions -> {
              assertThat(transferProcessDefinitions).contains("trustCenterAgent");
              assertThat(transferProcessDefinitions).contains("MII");
              assertThat(transferProcessDefinitions).contains("fhirStore");
            })
        .verifyComplete();
  }


  @Test
  void projectReturns404() {
    StepVerifier.create(
            client.get().uri("/api/v2/projects/doesNotExist").retrieve().bodyToMono(String.class))
        .verifyErrorSatisfies(
            error -> {
              assertThat(error)
                  .asInstanceOf(type(WebClientResponseException.class))
                  .satisfies(e -> assertThat(e.getStatusCode()).isEqualTo(NOT_FOUND));
            });
  }
}
