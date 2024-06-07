package care.smith.fts.cda.rest;

import care.smith.fts.cda.R4TransferProcessRunner;
import care.smith.fts.cda.TransferProcess;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/process")
public class TransferProcessController {

  private final R4TransferProcessRunner processRunner;
  private final ApplicationContext context;

  public TransferProcessController(R4TransferProcessRunner runner, ApplicationContext context) {
    this.processRunner = runner;
    this.context = context;
  }

  @PostMapping(value = "/{project}/start")
  List<Boolean> start(@PathVariable String project) {
    var process = context.getBean(project, TransferProcess.class);
    log.debug("Running process: {}", process);
    var result = processRunner.run(process);
    log.debug("Process run finished: {}", result);
    return result;
  }
}
