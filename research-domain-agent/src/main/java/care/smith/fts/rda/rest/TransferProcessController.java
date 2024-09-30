package care.smith.fts.rda.rest;

import static care.smith.fts.util.FhirUtils.resourceStream;
import static care.smith.fts.util.FhirUtils.toBundle;
import static care.smith.fts.util.HeaderTypes.X_PROGRESS;
import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON_VALUE;
import static care.smith.fts.util.error.ErrorResponseUtil.notFound;
import static com.google.common.base.Predicates.and;
import static java.util.function.Predicate.not;
import static org.springframework.http.HttpHeaders.CONTENT_LOCATION;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.rda.TransferProcessDefinition;
import care.smith.fts.rda.TransferProcessRunner;
import care.smith.fts.rda.TransferProcessRunner.Status;
import care.smith.fts.util.error.ErrorResponseUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v2/process")
@Validated
public class TransferProcessController {

  private final TransferProcessRunner processRunner;
  private final List<TransferProcessDefinition> processes;

  public TransferProcessController(
      TransferProcessRunner runner, List<TransferProcessDefinition> processes) {
    this.processRunner = runner;
    this.processes = processes;
  }

  @PostMapping(
      value = "/{project:[\\w-]+}/patient",
      consumes = APPLICATION_FHIR_JSON_VALUE,
      produces = APPLICATION_FHIR_JSON_VALUE)
  Mono<ResponseEntity<Object>> start(
      @PathVariable("project") String project,
      @Valid @NotNull @RequestBody Mono<Bundle> data,
      UriComponentsBuilder uriBuilder) {

    var process = findProcess(project);
    return process
        .map(transferProcessDefinition -> startProcess(data, uriBuilder, transferProcessDefinition))
        .orElseGet(
            () ->
                notFound(
                    new IllegalArgumentException(
                        "Project '%s' could not be found".formatted(project))));
  }

  private Mono<ResponseEntity<Object>> startProcess(
      Mono<Bundle> data,
      UriComponentsBuilder uriBuilder,
      TransferProcessDefinition transferProcessDefinition) {
    return data.map(TransferProcessController::fromPlainBundle)
        .doOnNext(b -> log.debug("Running process: {}", transferProcessDefinition))
        .map(tb -> processRunner.start(transferProcessDefinition, Mono.just(tb)))
        .doOnNext(id -> log.trace("projectId {}", id))
        .map(id -> generateJobUri(uriBuilder, id))
        .doOnNext(jobUri -> log.trace("jobUri {}", jobUri))
        .map(
            jobUri ->
                ResponseEntity.accepted()
                    .headers(h -> h.add(CONTENT_LOCATION, jobUri.toString()))
                    .build());
  }

  static TransportBundle fromPlainBundle(Bundle bundle) {
    log.trace("Converting from PlainBundle to TransportBundle");
    var bundleWithoutParameters =
        resourceStream(bundle)
            .filter(
                not(
                    and(
                        Parameters.class::isInstance,
                        p -> p.getIdPart().equals("transport-id-map"))))
            .collect(toBundle());
    var transportIdMapName =
        resourceStream(bundle)
            .filter(Parameters.class::isInstance)
            .filter(p -> p.getIdPart().equals("transport-id-map"))
            .map(Parameters.class::cast)
            .findFirst()
            .map(resource -> resource.getParameter("transport-id-map-name"))
            .map(ParametersParameterComponent::getValue)
            .map(StringType.class::cast)
            .map(PrimitiveType::getValue)
            .orElseThrow(
                () -> new IllegalArgumentException("Parameters 'transport-id-map' not found"));
    return new TransportBundle(bundleWithoutParameters, transportIdMapName);
  }

  private Optional<TransferProcessDefinition> findProcess(String project) {
    return processes.stream().filter(p -> p.project().equalsIgnoreCase(project)).findFirst();
  }

  private URI generateJobUri(UriComponentsBuilder uriBuilder, String id) {
    return uriBuilder.replacePath("api/v2/process/status/{id}").build(id);
  }

  @GetMapping("/status/{processId:[\\w-]+}")
  Mono<ResponseEntity<Status>> status(@PathVariable("processId") String processId) {
    log.trace("Process ID: {}", processId);
    return processRunner
        .status(processId)
        .map(s -> responseForStatus(s).body(s))
        .onErrorResume(ErrorResponseUtil::notFound);
  }

  private BodyBuilder responseForStatus(Status s) {
    return switch (s.phase()) {
      case RUNNING ->
          ResponseEntity.accepted()
              .headers(
                  h -> {
                    h.add(X_PROGRESS, "Running");
                    h.add(RETRY_AFTER, "3");
                  });
      case COMPLETED -> ResponseEntity.ok();
      case ERROR -> ResponseEntity.internalServerError();
    };
  }
}
