package care.smith.fts.rda.rest;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.rda.TransferProcess;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/api/v2/process")
public class TransferProcessController {

  private final R4TransferProcessRunner processRunner;
  private final ApplicationContext context;

  public TransferProcessController(R4TransferProcessRunner runner, ApplicationContext context) {
    this.processRunner = runner;
    this.context = context;
  }

  @PostMapping(value = "/{project}/start", consumes = "application/json")
  Flux<R4TransferProcessRunner.Result> start(
      @PathVariable String project, @RequestBody Flux<TransportBundle<Bundle>> data) {
    TransferProcess<Bundle> process = context.getBean(project, TransferProcess.class);
    log.debug("Running process: {}", process);
    var result = processRunner.run(process, data);
    log.debug("Process run finished: {}", result);
    return result;
  }
}
