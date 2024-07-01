package care.smith.fts.cda;

import reactor.core.publisher.Mono;

public interface TransferProcessRunner {
  String run(TransferProcess process);

  Mono<State> state(String id);

  record State(String id, boolean running, long bundlesSentCount, long patientsSkippedCount) {}
}
