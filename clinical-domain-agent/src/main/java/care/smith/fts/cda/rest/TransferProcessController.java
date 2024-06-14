package care.smith.fts.cda.rest;

import static reactor.core.publisher.Flux.error;

import care.smith.fts.cda.R4TransferProcessRunner.Result;
import care.smith.fts.cda.TransferProcess;
import care.smith.fts.cda.TransferProcessRunner;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/api/v2/process")
public class TransferProcessController {

  private final TransferProcessRunner processRunner;
  private final List<TransferProcess<Bundle>> processes;

  public TransferProcessController(
          TransferProcessRunner runner, List<TransferProcess<Bundle>> processes) {
    this.processRunner = runner;
    this.processes = processes;
  }

  @PostMapping(value = "/{project}/start")
  Flux<Result> start(@PathVariable String project) {
    var process = findProcess(project);
    if (process.isPresent()) {
      log.debug("Running process: {}", process);
      var result = processRunner.run(process.get());
      log.debug("Process run finished: {}", result);
      return result;
    } else {
      return error(new IllegalStateException("Project %s could not be found".formatted(project)));
    }
  }

  private Optional<TransferProcess<Bundle>> findProcess(String project) {
    return processes.stream().filter(p -> p.project().equalsIgnoreCase(project)).findFirst();
  }
}
