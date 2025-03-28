package care.smith.fts.cda.impl;

import static care.smith.fts.util.FhirUtils.resourceStream;
import static care.smith.fts.util.FhirUtils.toBundle;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Stream.concat;
import static java.util.stream.Stream.of;
import static org.springframework.http.HttpHeaders.CONTENT_LOCATION;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.OK;

import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.cda.BundleSender;
import care.smith.fts.util.MediaTypes;
import care.smith.fts.util.error.TransferProcessException;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Parameters;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
final class RDABundleSender implements BundleSender {
  private final RDABundleSenderConfig config;
  private final WebClient rdaClient;
  private final MeterRegistry meterRegistry;

  public RDABundleSender(
      RDABundleSenderConfig config, WebClient rdaClient, MeterRegistry meterRegistry) {
    this.config = config;
    this.rdaClient = rdaClient;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Mono<Result> send(TransportBundle bundle) {
    return sendBundle(toPlainBundle(requireNonNull(bundle))).map(v -> new Result());
  }

  private static Bundle toPlainBundle(TransportBundle transportBundle) {
    Parameters transportIdMap = new Parameters();
    transportIdMap.setId("transfer-id");
    transportIdMap.addParameter("id", transportBundle.transferId());
    return concat(of(transportIdMap), resourceStream(transportBundle.bundle())).collect(toBundle());
  }

  private Mono<ResponseEntity<Void>> sendBundle(Bundle bundle) {
    return rdaClient
        .post()
        .uri("/api/v2/process/{project}/patient", Map.of("project", config.project()))
        .headers(h -> h.setContentType(MediaTypes.APPLICATION_FHIR_JSON))
        .bodyValue(requireNonNull(bundle))
        .retrieve()
        .toBodilessEntity()
        .flatMap(this::processOrWaitForRDACompleted)
        .retryWhen(defaultRetryStrategy(meterRegistry, "sendBundleToRda"))
        .doOnError(e -> log.error("Unable to send Bundle to RDA: {}", e.getMessage()));
  }

  private Mono<ResponseEntity<Void>> processOrWaitForRDACompleted(ResponseEntity<Void> response) {
    if (response.getStatusCode() == OK) {
      return Mono.just(response);
    } else {
      return waitForRDACompleted(response);
    }
  }

  private Mono<ResponseEntity<Void>> waitForRDACompleted(ResponseEntity<Void> response) {
    return Mono.just(response)
        .flatMap(this::extractStatusUri)
        .doOnNext(uri -> log.trace("Status Uri: {}", uri))
        .flatMap(
            uri ->
                fetchStatus(uri)
                    .expand(
                        r -> fetchStatus(uri).delayElement(Duration.ofSeconds(getRetryAfter(r))))
                    .takeUntil(r -> r.getStatusCode() != ACCEPTED)
                    .take(10)
                    .last())
        .flatMap(
            r -> {
              if (r.getStatusCode() == OK) {
                return Mono.just(r);
              } else {
                log.error("Error: {}", r.getStatusCode());
                return Mono.error(new TransferProcessException("Error: " + r.getStatusCode()));
              }
            });
  }

  private Mono<ResponseEntity<Void>> fetchStatus(URI uri) {
    return rdaClient.get().uri(uri.toString()).retrieve().toBodilessEntity();
  }

  private Mono<URI> extractStatusUri(ResponseEntity<Void> response) {
    HttpStatusCode statusCode = response.getStatusCode();
    if (!statusCode.equals(ACCEPTED)) {
      return Mono.error(new TransferProcessException("Require ACCEPTED status"));
    }

    var uri = response.getHeaders().get(CONTENT_LOCATION);
    log.trace("uri {}", uri);
    if (uri == null || uri.isEmpty() || uri.getFirst().isBlank()) {
      return Mono.error(new TransferProcessException("Missing Content-Location"));
    }

    return Mono.just(URI.create(uri.getFirst()));
  }

  /**
   * @return the duration in seconds after which a retry may be performed
   */
  private static Long getRetryAfter(ResponseEntity<Void> response) {
    var retryAfter = response.getHeaders().get(RETRY_AFTER);
    if (retryAfter == null) {
      return 1L;
    } else {
      return retryAfter.stream().findFirst().map(RDABundleSender::parseRetryAfter).orElse(1L);
    }
  }

  private static long parseRetryAfter(String s) {
    try {
      return Long.parseLong(s);
    } catch (NumberFormatException e) {
      log.warn("Failed to parse Retry-After header: {}", s);
      return 1L;
    }
  }
}
