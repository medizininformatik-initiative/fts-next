package care.smith.fts.cda.impl;

import static care.smith.fts.util.FhirUtils.resourceStream;
import static care.smith.fts.util.FhirUtils.toBundle;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.of;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.cda.BundleSender;
import care.smith.fts.util.FhirUtils;
import care.smith.fts.util.MediaTypes;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
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
        .map(RDABundleSender::toPlainBundle)
        .flatMap(this::sendBundle)
        .reduce(0, (res, resp) -> res + 1)
        .map(Result::new);
  }

  private Mono<ResponseEntity<Void>> sendBundle(Bundle bundle) {
    log.info(FhirUtils.fhirResourceToString(bundle));
    return client
        .post()
        .uri(uri -> uri.pathSegment("api", "v2", "{project}", "patient").build(config.project()))
        .headers(h -> h.setContentType(MediaTypes.APPLICATION_FHIR_JSON))
        .bodyValue(requireNonNull(bundle))
        .retrieve()
        .toBodilessEntity();
  }

  private static Bundle toPlainBundle(TransportBundle transportBundle) {
    Parameters transportIds = new Parameters();
    transportIds.setId("transport-ids");
    transportBundle.transportIds().forEach(id -> transportIds.addParameter("transport-id", id));
    return concat(of(transportIds), resourceStream(transportBundle.bundle())).collect(toBundle());
  }
}
