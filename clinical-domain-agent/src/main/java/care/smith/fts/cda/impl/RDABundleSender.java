package care.smith.fts.cda.impl;


import ca.uhn.fhir.context.FhirContext;
import care.smith.fts.api.BundleSender;
import care.smith.fts.api.ConsentedPatient;
import java.io.*;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.http.MediaType;
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
  public Mono<Result> send(Flux<Bundle> bundle, ConsentedPatient patient) {
    return client
        .post()
        .uri("/api/v2/process/" + config.project())
        .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
        .bodyValue(bundle)
        .retrieve()
        .bodyToMono(Result.class);
  }
}
