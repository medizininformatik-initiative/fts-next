package care.smith.fts.cda;

import care.smith.fts.api.ConsentedPatient;
import lombok.Builder;
import reactor.core.publisher.Flux;

public interface TransferProcessRunner {
  Flux<Result> run(TransferProcess process);

  @Builder
  public record Result(
      ConsentedPatient patient,
      int bundlesSent,
      long selectedResources,
      long deidentifedResource,
      long transportIds) {}
}
