package care.smith.fts.rda.impl;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;

import care.smith.fts.api.rda.BundleSender;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

final class FhirStoreBundleSender implements BundleSender {
  private final WebClient client;

  public FhirStoreBundleSender(WebClient client) {
    this.client = client;
  }

  @Override
  public Mono<Result> send(Bundle bundle) {
    return client
        .post()
        .headers(h -> h.setContentType(APPLICATION_FHIR_JSON))
        .bodyValue(toTransactionBundle(bundle))
        .retrieve()
        .toBodilessEntity()
        .map(b -> new Result());
  }

  private static Bundle toTransactionBundle(Bundle bundle) {
    Bundle transactionBundle = new Bundle();
    transactionBundle.setType(Bundle.BundleType.TRANSACTION);
    transactionBundle.addEntry(new BundleEntryComponent().setResource(bundle));
    return transactionBundle;
  }
}
