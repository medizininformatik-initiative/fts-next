package care.smith.fts.cda;

import java.util.List;
import reactor.core.publisher.Mono;

public interface TransferProcessRunner {
  Mono<Result> run(TransferProcess process);

  record PatientResult(
      long bundlesSentCount,
      long selectedResourcesCount,
      long deidentifedResourcesCount,
      long transportIdsCount) {}

  record Result(long bunesSentCount, long patientErrorsCount, List<PatientResult> patientResults) {}
}
