package care.smith.fts.rda.impl;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.fhir.FhirUtils.resourceStream;
import static org.hl7.fhir.r4.model.Bundle.BundleType.TRANSACTION;

import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.util.RetryStrategy;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
final class FhirStoreBundleSender implements BundleSender {
  private final WebClient hdsClient;
  private final RetryStrategy retryStrategy;
  private final Bulkhead bulkhead;

  public FhirStoreBundleSender(
      WebClient hdsClient, RetryStrategy retryStrategy, Bulkhead bulkhead) {
    this.hdsClient = hdsClient;
    this.retryStrategy = retryStrategy;
    this.bulkhead = bulkhead;
  }

  @Override
  public Mono<Result> send(Bundle bundle) {
    log.trace("Sending bundle");
    return hdsClient
        .post()
        .uri("")
        .headers(h -> h.setContentType(APPLICATION_FHIR_JSON))
        .bodyValue(toTransactionBundle(bundle))
        .retrieve()
        .toBodilessEntity()
        .retryWhen(retryStrategy.forRequest("sendBundleToHds"))
        .doOnNext(res -> log.trace("Response received: {}", res))
        .doOnError(err -> log.debug("Error received", err))
        .map(b -> new Result())
        .transformDeferred(BulkheadOperator.of(bulkhead));
  }

  private static Bundle toTransactionBundle(Bundle bundle) {
    Bundle transactionBundle = new Bundle();
    resourceStream(bundle)
        .map(FhirStoreBundleSender::createPutEntry)
        .forEach(transactionBundle::addEntry);
    transactionBundle.setType(TRANSACTION);
    return transactionBundle;
  }

  private static BundleEntryComponent createPutEntry(Resource r) {
    var value =
        new Bundle.BundleEntryRequestComponent()
            .setMethod(Bundle.HTTPVerb.PUT)
            .setUrl("%s/%s".formatted(r.getResourceType().name(), r.getIdPart()));
    return new BundleEntryComponent().setRequest(value).setResource(r);
  }
}
