package care.smith.fts.cda;

import reactor.core.publisher.Mono;

public interface TransferProcessRunner {
  String run(TransferProcess process);

  Mono<State> state(String processId);

  record State(String processId, Status status, long bundlesSentCount, long patientsSkippedCount) {}

  enum Status {
    QUEUED,
    RUNNING,
    COMPLETED
  }
}
