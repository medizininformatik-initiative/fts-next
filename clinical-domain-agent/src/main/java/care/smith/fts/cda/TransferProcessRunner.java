package care.smith.fts.cda;

import reactor.core.publisher.Mono;

public interface TransferProcessRunner {
  String run(TransferProcess process);

  Mono<State> state(String id);

  record State(String id, Status status, long bundlesSentCount, long patientsSkippedCount) {}

  enum Status {
    QUEUED,
    RUNNING,
    COMPLETED
  }
}
