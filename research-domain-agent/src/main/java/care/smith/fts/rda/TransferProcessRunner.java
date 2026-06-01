package care.smith.fts.rda;

import care.smith.fts.api.TransportBundle;
import reactor.core.publisher.Mono;

public interface TransferProcessRunner {

  /**
   * Attempts to admit a transfer process. Admission applies backpressure: it may reject the bundle
   * if the system is at capacity (global hard cap) or the project is over its elastic fair share.
   *
   * @return {@link StartResult.Accepted} carrying the new processId on admission, otherwise {@link
   *     StartResult.Rejected}.
   */
  StartResult start(TransferProcessDefinition process, Mono<TransportBundle> data);

  /** Outcome of an admission attempt. */
  sealed interface StartResult permits StartResult.Accepted, StartResult.Rejected {

    record Accepted(String processId) implements StartResult {}

    record Rejected() implements StartResult {}

    static StartResult accepted(String processId) {
      return new Accepted(processId);
    }

    static StartResult rejected() {
      return new Rejected();
    }
  }

  record Result(long receivedResources, long sentResources) {}

  Mono<Status> status(String processId);

  record Status(String processId, Phase phase, long receivedResources, long sentResources) {}

  enum Phase {
    RUNNING,
    COMPLETED,
    ERROR
  }
}
