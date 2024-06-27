package care.smith.fts.cda;

import java.util.List;
import lombok.Builder;
import reactor.core.publisher.Mono;

public interface TransferProcessRunner {
  Mono<Result> run(TransferProcess process);

  @Builder
  record PatientResult(
      long bundlesSentCount,
      long selectedResourcesCount,
      long deidentifedResourcesCount,
      long transportIdsCount) {}

  record Result(long bunesSentCount, long patientErrorsCount, List<PatientResult> patientResults) {}
}
