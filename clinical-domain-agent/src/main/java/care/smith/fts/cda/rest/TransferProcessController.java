package care.smith.fts.cda.rest;

import static care.smith.fts.util.HeaderTypes.X_PROGRESS;
import static care.smith.fts.util.error.ErrorResponseUtil.notFound;

import care.smith.fts.cda.TransferProcessDefinition;
import care.smith.fts.cda.TransferProcessRunner;
import care.smith.fts.cda.TransferProcessStatus;
import care.smith.fts.util.error.ErrorResponseUtil;
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

  private final TransferProcessRunner processRunner;
  private final List<TransferProcessDefinition> processes;

  public TransferProcessController(
      TransferProcessRunner runner, List<TransferProcessDefinition> processes) {
    this.processRunner = runner;
    this.processes = processes;
  }

  @PostMapping(value = "/{project:[\\w-]+}/start")
  Mono<ResponseEntity<Object>> start(
      @PathVariable("project") String project,
      UriComponentsBuilder uriBuilder,
      @RequestBody(required = false) List<String> pids) {
    var process = findProcess(project);
    if (process.isPresent()) {
      log.debug("Running process: {}", process.get());

      var id = processRunner.start(process.get(), Optional.ofNullable(pids).orElse(List.of()));
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
      return notFound(
          new IllegalArgumentException("Project '%s' could not be found".formatted(project)));
    }
  }

  private URI generateJobUri(UriComponentsBuilder uriBuilder, String id) {
    return uriBuilder.replacePath("api/v2/process/status/{id}").build(id);
  }

  @GetMapping("/status/{processId:[\\w-]+}")
  Mono<ResponseEntity<TransferProcessStatus>> status(
      @PathVariable(value = "processId") String processId) {
    return processRunner
        .status(processId)
        .map(s -> responseForStatus(s).body(s))
        .onErrorResume(ErrorResponseUtil::notFound);
  }

  @GetMapping("/statuses")
  Mono<ResponseEntity<List<TransferProcessStatus>>> statuses() {
    return processRunner.statuses().map(s -> ResponseEntity.ok().body(s));
  }

  private static BodyBuilder responseForStatus(TransferProcessStatus s) {
    return switch (s.phase()) {
      case QUEUED -> ResponseEntity.accepted().headers(h -> h.add(X_PROGRESS, "Queued"));
      case RUNNING -> ResponseEntity.accepted().headers(h -> h.add(X_PROGRESS, "Running"));
      case COMPLETED, COMPLETED_WITH_ERROR -> ResponseEntity.ok();
      case FATAL -> ResponseEntity.internalServerError();
    };
  }

  private Optional<TransferProcessDefinition> findProcess(String project) {
    return processes.stream().filter(p -> p.project().equalsIgnoreCase(project)).findFirst();
  }
}
