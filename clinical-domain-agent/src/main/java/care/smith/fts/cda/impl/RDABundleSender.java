package care.smith.fts.cda.impl;

import static java.util.Objects.requireNonNull;

import care.smith.fts.api.BundleSender;
import care.smith.fts.api.TransportBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

final class RDABundleSender implements BundleSender<Bundle> {
  private final RDABundleSenderConfig config;
  private final WebClient client;

  public RDABundleSender(RDABundleSenderConfig config, WebClient client) {
    this.config = config;
    this.client = client;
  }

  @Override
  public Mono<Result> send(Flux<TransportBundle<Bundle>> bundles) {
    return bundles.flatMap(this::sendBundle).reduce(0, (res, resp) -> res + 1).map(Result::new);
  }

  private Mono<ResponseEntity<Void>> sendBundle(TransportBundle<Bundle> bundle) {
    return client
        .post()
        .uri("/api/v2/process/" + config.project())
        .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
        .bodyValue(requireNonNull(bundle))
        .retrieve()
        .toBodilessEntity();
  }
}
