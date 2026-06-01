package care.smith.fts.rda.impl;

import static care.smith.fts.util.MediaTypes.APPLICATION_FHIR_JSON;
import static care.smith.fts.util.fhir.FhirUtils.resourceStream;
import static org.hl7.fhir.r4.model.Bundle.BundleType.TRANSACTION;

import care.smith.fts.api.rda.BundleSender;
import care.smith.fts.util.RetryStrategy;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
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
  private final String destinationId;
  private final int sendConcurrency;

  public FhirStoreBundleSender(
      WebClient hdsClient, RetryStrategy retryStrategy, String baseUrl, int sendConcurrency) {
    this.hdsClient = hdsClient;
    this.retryStrategy = retryStrategy;
    this.destinationId = normalizeBaseUrl(baseUrl);
    this.sendConcurrency = sendConcurrency;
  }

  /**
   * HDS grouping key. Normalises the case-insensitive parts of the URL (scheme + host) and trailing
   * slashes so projects pointing at the same Blaze (modulo cosmetic URL differences) share one
   * drainer. The path is left untouched, since path segments are case-sensitive per RFC 3986.
   */
  @Override
  public String destinationId() {
    return destinationId;
  }

  @Override
  public int sendConcurrency() {
    return sendConcurrency;
  }

  private static String normalizeBaseUrl(String baseUrl) {
    var trimmed = baseUrl.strip();
    while (trimmed.endsWith("/")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1);
    }
    try {
      var uri = URI.create(trimmed);
      if (uri.getScheme() != null && uri.getHost() != null) {
        return new URI(
                uri.getScheme().toLowerCase(Locale.ROOT),
                uri.getUserInfo(),
                uri.getHost().toLowerCase(Locale.ROOT),
                uri.getPort(),
                uri.getPath(),
                uri.getQuery(),
                uri.getFragment())
            .toString();
      }
    } catch (IllegalArgumentException | URISyntaxException e) {
      log.warn("Could not parse HDS base URL '{}' for grouping; using the raw value", baseUrl);
    }
    return trimmed;
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
        .map(b -> new Result());
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
