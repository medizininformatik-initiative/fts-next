package care.smith.fts.cda.rest;

import static care.smith.fts.util.error.ErrorResponseUtil.internalServerError;

import care.smith.fts.cda.TransferProcessDefinition;
import care.smith.fts.cda.TransferProcessRunner;
import care.smith.fts.cda.TransferProcessRunner.Status;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseEntity.BodyBuilder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v2/process")
public class TransferProcessController {
  private static final String X_PROGRESS_HEADER = "X-Progress";

  private final TransferProcessRunner processRunner;
  private final List<TransferProcessDefinition> processes;

  public TransferProcessController(
      TransferProcessRunner runner, List<TransferProcessDefinition> processes) {
    this.processRunner = runner;
    this.processes = processes;
  }

  @PostMapping(value = "/{project:[\\w-]+}/start")
  Mono<ResponseEntity<Object>> start(
      @PathVariable("project") String project, UriComponentsBuilder uriBuilder) {
    var process = findProcess(project);
    if (process.isPresent()) {
      log.debug("Running process: {}", process.get());
      var id = processRunner.start(process.get());
      var jobUri = generateJobUri(uriBuilder, id);
      return processRunner
          .status(id)
          .map(
              s ->
                  ResponseEntity.accepted()
                      .headers(h -> h.add("Content-Location", jobUri.toString()))
                      .build());
    } else {
      log.warn("Project '{}' not found", project);
      return internalServerError(
          new IllegalStateException("Project '%s' could not be found".formatted(project)));
    }
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
      case RUNNING -> ResponseEntity.accepted().headers(h -> h.add(X_PROGRESS_HEADER, "Running"));
      case COMPLETED, ERROR -> ResponseEntity.ok();
    };
  }

  private Optional<TransferProcessDefinition> findProcess(String project) {
    return processes.stream().filter(p -> p.project().equalsIgnoreCase(project)).findFirst();
  }
}
