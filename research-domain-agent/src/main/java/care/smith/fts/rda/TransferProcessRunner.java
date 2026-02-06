package care.smith.fts.rda;

import static care.smith.fts.util.fhir.FhirTag.addTagToAllResources;

import care.smith.fts.api.TransportBundle;
import org.hl7.fhir.r4.model.Bundle;
import reactor.core.publisher.Mono;

public interface TransferProcessRunner {

  String start(TransferProcessDefinition process, Mono<TransportBundle> data);

  record Result(long receivedResources, long sentResources) {}

  Mono<Status> status(String processId);

  record Status(String processId, Phase phase, long receivedResources, long sentResources) {}

  enum Phase {
    RUNNING,
    COMPLETED,
    ERROR
  }

  static Bundle tagResources(Bundle b) {
    return addTagToAllResources(
        b,
        "https://medizininformatik-initiative.github.io/fts-next/fhir/CodeSystem/FTS_Tags",
        "Processed",
        "processed");
  }
}
