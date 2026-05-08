package care.smith.fts.cda;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import reactor.core.publisher.Mono;

public interface TransferProcessRunner {
  String start(TransferProcessDefinition process, @NotNull List<String> identifiers);

  Mono<List<TransferProcessStatus>> statuses();

  Mono<TransferProcessStatus> status(String processId);

  Mono<List<PatientError>> failedPatients(String processId);

  enum Phase {
    QUEUED,
    RUNNING,
    COMPLETED,
    COMPLETED_WITH_ERROR,
    FATAL
  }

  enum Step {
    SELECT_DATA("select data"),
    DEIDENTIFY("deidentify bundle"),
    SEND_BUNDLE("send bundle");

    private final String displayName;

    Step(String displayName) {
      this.displayName = displayName;
    }

    @Override
    public String toString() {
      return displayName;
    }
  }

  record PatientError(String patientId, Step step, String errorMessage) {}
}
