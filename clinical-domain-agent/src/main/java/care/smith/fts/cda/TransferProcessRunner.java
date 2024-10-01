package care.smith.fts.cda;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import reactor.core.publisher.Mono;

public interface TransferProcessRunner {
  String start(TransferProcessDefinition process, @NotNull List<String> pids);

  Mono<Status> status(String processId);

  record Status(String processId, Phase phase, long bundlesSentCount, long bundlesSkippedCount) {}

  enum Phase {
    QUEUED,
    RUNNING,
    COMPLETED,
    ERROR
  }
}
