package care.smith.fts.cda;

import care.smith.fts.api.ConsentedPatient;
import java.util.List;
import lombok.Builder;
import reactor.core.publisher.Mono;

public interface TransferProcessRunner {
  Mono<Result> run(TransferProcess process);

  @Builder
  record PatientResult(
      ConsentedPatient patient,
      long bundlesSent,
      long selectedResources,
      long deidentifedResource,
      long transportIds) {}

  record Result(long bundleCount, long errorCount, List<PatientResult> patientResults) {}
}
