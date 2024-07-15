package care.smith.fts.cda;

import reactor.core.publisher.Mono;

public interface TransferProcessRunner {
  String start(TransferProcessDefinition process);

  Mono<Status> status(String processId);

  record Status(String processId, Phase phase, long bundlesSentCount, long patientsSkippedCount) {}

  enum Phase {
    QUEUED,
    RUNNING,
    COMPLETED,
    ERROR
  }
}
