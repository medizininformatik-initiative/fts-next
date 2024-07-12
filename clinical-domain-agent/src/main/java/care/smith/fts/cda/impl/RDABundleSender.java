package care.smith.fts.cda.impl;

import static care.smith.fts.util.FhirUtils.resourceStream;
import static care.smith.fts.util.FhirUtils.toBundle;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.of;
import static org.springframework.http.HttpHeaders.CONTENT_LOCATION;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.cda.BundleSender;
import care.smith.fts.util.MediaTypes;
import care.smith.fts.util.error.TransferProcessException;
import java.time.Duration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

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

  private static Bundle toPlainBundle(TransportBundle transportBundle) {
    Parameters transportIds = new Parameters();
    transportIds.setId("transport-ids");
    transportBundle.transportIds().forEach(id -> transportIds.addParameter("transport-id", id));
    return concat(of(transportIds), resourceStream(transportBundle.bundle())).collect(toBundle());
  }

  private Mono<ResponseEntity<Void>> sendBundle(Bundle bundle) {
    return client
        .post()
        .uri(uri -> uri.pathSegment("api", "v2", "{project}", "patient").build(config.project()))
        .headers(h -> h.setContentType(MediaTypes.APPLICATION_FHIR_JSON))
        .bodyValue(requireNonNull(bundle))
        .exchangeToMono(this::waitForRDACompleted);
  }

  private Mono<ResponseEntity<Void>> waitForRDACompleted(ClientResponse clientResponse) {
    var uri = clientResponse.headers().header(CONTENT_LOCATION);
    if (uri.isEmpty()) {
      return Mono.error(new TransferProcessException("Missing Content-Location"));
    }

    return client
        .get()
        .uri(uri.getFirst())
        .retrieve()
        .onStatus(
            s -> s == HttpStatus.ACCEPTED,
            c -> Mono.error(new RetryAfterException(getRetryAfter(c))))
        .toBodilessEntity()
        .retryWhen(retrySpec());
  }

  private static Long getRetryAfter(ClientResponse c) {
    return c.headers().header(RETRY_AFTER).stream()
        .findFirst()
        .map(
            s -> {
              try {
                return Long.parseLong(s);
              } catch (NumberFormatException e) {
                log.warn("Failed to parse Retry-After header: {}", s);
                return 1L;
              }
            })
        .orElse(1L);
  }

  private Retry retrySpec() {
    return Retry.max(10)
        .filter(t -> t instanceof RetryAfterException)
        .doBeforeRetryAsync(signal -> Mono.delay(calculateDelay(signal.failure())).then())
        .onRetryExhaustedThrow(
            (spec, signal) -> new TransferProcessException("RDABundleSender retry exhausted"));
  }

  private static Duration calculateDelay(Throwable failure) {
    if (failure instanceof RetryAfterException) {
      return Duration.ofSeconds(((RetryAfterException) failure).getRetryAfter());
    } else {
      return Duration.ofSeconds(1L);
    }
  }

  @Getter
  private static class RetryAfterException extends RuntimeException {
    private final Long retryAfter;

    public RetryAfterException(long retryAfter) {
      this.retryAfter = retryAfter;
    }
  }
}
