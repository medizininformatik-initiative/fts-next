package care.smith.fts.cda.rest;

import care.smith.fts.cda.R4TransferProcessRunner;
import care.smith.fts.cda.TransferProcess;
import care.smith.fts.cda.TransferProcessConfig;
import care.smith.fts.cda.TransferProcessFactory;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
public class TransferProcessController {

  private final TransferProcessFactory processFactory;
  private final R4TransferProcessRunner processRunner;

  public TransferProcessController(TransferProcessFactory processFactory, R4TransferProcessRunner processRunner) {
    this.processFactory = processFactory;
    this.processRunner = processRunner;
  }

  @PostMapping(value = "/process/start", consumes = "application/json")
  Object start(@RequestBody TransferProcessConfig processDefinition) {
    log.info("Running process: {}", processDefinition);
    TransferProcess transferProcess = processFactory.create(processDefinition);
    log.debug("Assembled process: {}", transferProcess);
    List<Boolean> result = processRunner.run(transferProcess);
    log.debug("Process run finished: {}", result);
    return result;
  }
}
