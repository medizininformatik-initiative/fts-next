package care.smith.fts.cda;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import reactor.core.publisher.Mono;

public interface TransferProcessRunner {
  String start(TransferProcessDefinition process, @NotNull List<String> identifiers);

  Mono<List<TransferProcessStatus>> statuses();

  Mono<TransferProcessStatus> status(String processId);

  enum Phase {
    QUEUED,
    RUNNING,
    COMPLETED,
    COMPLETED_WITH_ERROR,
    FATAL
  }
}
