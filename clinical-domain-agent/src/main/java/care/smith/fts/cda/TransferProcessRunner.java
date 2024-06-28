package care.smith.fts.cda;

import reactor.core.publisher.Mono;

public interface TransferProcessRunner {
  Mono<SummaryResult> run(TransferProcess process);

  record PatientResult(
      long bundlesSentCount,
      long selectedResourcesCount,
      long deidentifedResourcesCount,
      long transportIdsCount) {}

  record SummaryResult(long bundlesSentCount, long patientsSkippedCount) {}
}
