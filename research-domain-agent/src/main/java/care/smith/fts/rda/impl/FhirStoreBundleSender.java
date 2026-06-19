package care.smith.fts.rda.impl;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.fhir.FhirUtils.resourceStream;
import static java.lang.Integer.parseInt;
import static java.util.Optional.empty;
import static java.util.function.Predicate.not;
import static org.hl7.fhir.r4.model.Bundle.BundleType.TRANSACTION;

import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.util.RetryStrategy;
import care.smith.fts.util.error.TransferProcessException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleEntryResponseComponent;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
final class FhirStoreBundleSender implements BundleSender {
  private final WebClient hdsClient;
  private final RetryStrategy retryStrategy;
  private final String destinationId;

  public FhirStoreBundleSender(
      WebClient hdsClient, RetryStrategy retryStrategy, String destinationId) {
    this.hdsClient = hdsClient;
    this.retryStrategy = retryStrategy;
    this.destinationId = destinationId;
  }

  @Override
  public String destinationId() {
    return destinationId;
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
        .bodyToMono(Bundle.class)
        .retryWhen(retryStrategy.forRequest("sendBundleToHds"))
        .switchIfEmpty(Mono.just(new Bundle()))
        .doOnNext(res -> log.trace("Transaction response: {} entries", res.getEntry().size()))
        .doOnError(err -> log.debug("Error received", err))
        .flatMap(FhirStoreBundleSender::validateTransactionResponse);
  }

  static Mono<Result> validateTransactionResponse(Bundle responseBundle) {
    var failures =
        responseBundle.getEntry().stream()
            .filter(not(FhirStoreBundleSender::hasHttpSuccess))
            .map(FhirStoreBundleSender::entryStatus)
            .toList();
    if (failures.isEmpty()) {
      return Mono.just(new Result());
    } else {
      log.warn("Transaction failed: {} non-2xx entries: {}", failures.size(), failures);
      return Mono.error(
          new TransferProcessException(
              "Transaction response contains non-2xx entries: " + failures));
    }
  }

  static boolean hasHttpSuccess(BundleEntryComponent entry) {
    return Optional.of(entry)
        .filter(BundleEntryComponent::hasResponse)
        .map(BundleEntryComponent::getResponse)
        .filter(BundleEntryResponseComponent::hasStatus)
        .map(BundleEntryResponseComponent::getStatus)
        .flatMap(FhirStoreBundleSender::parseStatus)
        .map(HttpStatus::resolve)
        .map(HttpStatus::is2xxSuccessful)
        .orElse(false);
  }

  private static Optional<Integer> parseStatus(String status) {
    try {
      return Optional.of(status).filter(s -> s.length() >= 3).map(s -> parseInt(s.substring(0, 3)));
    } catch (NumberFormatException e) {
      return empty();
    }
  }

  private static String entryStatus(BundleEntryComponent entry) {
    if (!entry.hasResponse()) {
      return "<no response>";
    }
    var response = entry.getResponse();
    if (!response.hasStatus()) {
      return "<no status>";
    }
    return response.getStatus();
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
