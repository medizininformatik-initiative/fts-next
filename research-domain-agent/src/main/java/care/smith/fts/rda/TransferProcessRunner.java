package care.smith.fts.rda;

import care.smith.fts.api.TransportBundle;
import reactor.core.publisher.Mono;

public interface TransferProcessRunner {
  String start(TransferProcessDefinition process, Mono<TransportBundle> data);

  record Result(long receivedResources, long sentResources) {}

  Mono<Status> status(String processId);

  record Status(String processId, Phase phase, long receivedResources, long sentResources) {}

  enum Phase {
    QUEUED,
    RUNNING,
    COMPLETED,
    ERROR
  }
}
