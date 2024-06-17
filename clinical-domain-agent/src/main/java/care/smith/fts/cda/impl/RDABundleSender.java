package care.smith.fts.cda.impl;

import static care.smith.fts.util.FhirUtils.resourceStream;
import static care.smith.fts.util.FhirUtils.toBundle;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.of;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.cda.BundleSender;
import java.util.Optional;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

final class RDABundleSender implements BundleSender {
  private final RDABundleSenderConfig config;
  private final WebClient client;

  public RDABundleSender(RDABundleSenderConfig config, WebClient client) {
    this.config = config;
    this.client = client;
  }

  @Override
  public Mono<Result> send(Flux<TransportBundle> bundles) {
    return bundles
        .map(RDABundleSender::toPatientBundle)
        .flatMap(this::sendBundle)
        .reduce(0, (res, resp) -> res + 1)
        .map(Result::new);
  }

  private Mono<ResponseEntity<Void>> sendBundle(Bundle bundle) {
    return client
        .post()
        .uri("/api/v2/process/" + config.project())
        .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
        .bodyValue(requireNonNull(bundle))
        .retrieve()
        .toBodilessEntity();
  }

  public static Bundle toPatientBundle(TransportBundle transportBundle) {
    Optional<Resource> patient =
        resourceStream(transportBundle.bundle()).filter(Patient.class::isInstance).findFirst();
    if (patient.isPresent()) {
      return toPatientBundle(transportBundle, patient.get());
    } else {
      throw new IllegalArgumentException("TransportBundle contains no Patient");
    }
  }

  private static Bundle toPatientBundle(TransportBundle transportBundle, Resource resource) {
    Parameters transportIds = new Parameters();
    transportBundle.transportIds().forEach(id -> transportIds.addParameter("transportId", id));
    return concat(
            of(resource, transportIds),
            resourceStream(transportBundle.bundle()).filter(not(Patient.class::isInstance)))
        .collect(toBundle());
  }
}
