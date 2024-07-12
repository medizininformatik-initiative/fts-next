package care.smith.fts.rda.rest;

import static care.smith.fts.util.FhirUtils.resourceStream;
import static care.smith.fts.util.FhirUtils.toBundle;
import static care.smith.fts.util.HeaderTypes.X_PROGRESS_HEADER;
import static care.smith.fts.util.error.ErrorResponseUtil.internalServerError;
import static com.google.common.base.Predicates.and;
import static java.util.function.Predicate.not;
import static org.springframework.http.HttpHeaders.CONTENT_LOCATION;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.rda.TransferProcessDefinition;
import care.smith.fts.rda.TransferProcessRunner;
import care.smith.fts.rda.TransferProcessRunner.Result;
import care.smith.fts.rda.TransferProcessRunner.Status;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v2")
public class TransferProcessController {

  private final TransferProcessRunner processRunner;
  private final List<TransferProcessDefinition> processes;

  public TransferProcessController(
      TransferProcessRunner runner, List<TransferProcessDefinition> processes) {
    this.processRunner = runner;
    this.processes = processes;
  }

  @PostMapping(value = "/{project:[\\w-]+}/patient", consumes = "application/fhir+json")
  Mono<ResponseEntity<Result>> start(
      @PathVariable("project") String project,
      @RequestBody Mono<Bundle> data,
      UriComponentsBuilder uriBuilder) {
    var process = findProcess(project);
    if (process.isPresent()) {
      log.debug("Running process: {}", process.get());
      var id =
          processRunner.start(process.get(), data.map(TransferProcessController::fromPlainBundle));

      var jobUri = generateJobUri(uriBuilder, id);
      return processRunner
          .status(id)
          .map(
              s ->
                  ResponseEntity.accepted()
                      .headers(h -> h.add(CONTENT_LOCATION, jobUri.toString()))
                      .build());

    } else {
      return internalServerError(
          new IllegalStateException("Project '%s' could not be found".formatted(project)));
    }
  }

  static TransportBundle fromPlainBundle(Bundle bundle) {
    log.trace("Converting from PlainBundle to TransportBundle");
    var bundleWithoutParameters =
        resourceStream(bundle)
            .filter(
                not(and(Parameters.class::isInstance, p -> p.getIdPart().equals("transport-ids"))))
            .collect(toBundle());
    var transportIds =
        resourceStream(bundle)
            .filter(Parameters.class::isInstance)
            .filter(p -> p.getIdPart().equals("transport-ids"))
            .map(Parameters.class::cast)
            .findFirst()
            .map(TransferProcessController::extractTransportIds)
            .orElse(Set.of());
    return new TransportBundle(bundleWithoutParameters, transportIds);
  }

  private static Set<String> extractTransportIds(Parameters resource) {
    return resource.getParameters("transport-id").stream()
        .map(Parameters.ParametersParameterComponent::getValue)
        .map(StringType.class::cast)
        .map(PrimitiveType::getValue)
        .collect(Collectors.toSet());
  }

  private Optional<TransferProcessDefinition> findProcess(String project) {
    return processes.stream().filter(p -> p.project().equalsIgnoreCase(project)).findFirst();
  }

  private URI generateJobUri(UriComponentsBuilder uriBuilder, String id) {
    return uriBuilder.replacePath("api/v2/process/status/{id}").build(id);
  }

  @GetMapping("/status/{processId:[\\w-]+}")
  Mono<ResponseEntity<Status>> status(@PathVariable("processId") String processId) {
    return processRunner.status(processId).map(s -> responseForStatus(s).body(s));
  }

  private static BodyBuilder responseForStatus(Status s) {
    return switch (s.phase()) {
      case QUEUED -> ResponseEntity.accepted().headers(h -> h.add(X_PROGRESS_HEADER, "Queued"));
      case RUNNING ->
          ResponseEntity.accepted()
              .headers(
                  h -> {
                    h.add(X_PROGRESS_HEADER, "Running");
                    h.add(RETRY_AFTER, "1");
                  });
      case COMPLETED, ERROR -> ResponseEntity.ok();
    };
  }
}
