package care.smith.fts.cda.rest;

import care.smith.fts.cda.TransferProcess;
import care.smith.fts.cda.TransferProcessRunner;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/v2/process")
public class TransferProcessController {

  private final TransferProcessRunner processRunner;
  private final List<TransferProcess> processes;

  public TransferProcessController(TransferProcessRunner runner, List<TransferProcess> processes) {
    this.processRunner = runner;
    this.processes = processes;
  }

  @PostMapping(value = "/{project}/start")
  Mono<TransferProcessRunner.Result> start(@PathVariable("project") String project) {
    var process = findProcess(project);
    if (process.isPresent()) {
      log.debug("Running process: {}", process.get());
      return processRunner
          .run(process.get())
          .doOnNext(result -> log.debug("Process run finished: {}", result))
          .doOnCancel(() -> log.warn("Process run cancelled"))
          .doOnError(err -> log.debug("Process run errored", err));
    } else {
      return Mono.error(
          new IllegalStateException("Project %s could not be found".formatted(project)));
    }
  }

  private Optional<TransferProcess> findProcess(String project) {
    return processes.stream().filter(p -> p.project().equalsIgnoreCase(project)).findFirst();
  }
}
