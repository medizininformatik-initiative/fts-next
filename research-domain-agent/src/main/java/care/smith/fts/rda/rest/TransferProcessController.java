package care.smith.fts.rda.rest;

import static reactor.core.publisher.Mono.error;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.rda.BundleSender.Result;
import care.smith.fts.rda.TransferProcess;
import care.smith.fts.rda.TransferProcessRunner;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
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

  @PostMapping(value = "/{project}/start", consumes = "application/json")
  Mono<Result> start(@PathVariable String project, @RequestBody Flux<TransportBundle> data) {
    var process = findProcess(project);
    if (process.isPresent()) {
      log.debug("Running process: {}", process);
      var result = processRunner.run(process.get(), data);
      log.debug("Process run finished: {}", result);
      return result;
    } else {
      return error(new IllegalStateException("Project %s could not be found".formatted(project)));
    }
  }

  private Optional<TransferProcess> findProcess(String project) {
    return processes.stream().filter(p -> p.project().equalsIgnoreCase(project)).findFirst();
  }
}
