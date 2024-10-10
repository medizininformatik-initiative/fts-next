package care.smith.fts.tca.deidentification;

import care.smith.fts.util.MediaTypes;
import care.smith.fts.util.RetryStrategies;
import care.smith.fts.util.error.UnknownDomainException;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class GpasClient {
  private final WebClient httpClient;
  private final MeterRegistry meterRegistry;

  public GpasClient(
      @Qualifier("gpasFhirHttpClient") WebClient httpClient, MeterRegistry meterRegistry) {
    this.httpClient = httpClient;
    this.meterRegistry = meterRegistry;
  }

  /**
   * @return Flux of (id, pid) tuples
   */
  public Mono<String> fetchOrCreatePseudonyms(String domain, String id) {
    var params =
        Map.of(
            "resourceType",
            "Parameters",
            "parameter",
            List.of(
                Map.of("name", "target", "valueString", domain),
                Map.of("name", "original", "valueString", id)));

    log.trace("fetchOrCreatePseudonyms for domain: {} and {}", domain, id);

    return httpClient
        .post()
        .uri("/$pseudonymizeAllowCreate")
        .headers(h -> h.setContentType(MediaTypes.APPLICATION_FHIR_JSON))
        .bodyValue(params)
        .headers(h -> h.setAccept(List.of(MediaTypes.APPLICATION_FHIR_JSON)))
        .retrieve()
        .onStatus(r1 -> r1.equals(HttpStatus.BAD_REQUEST), GpasClient::handleGpasBadRequest)
        .bodyToMono(GpasParameterResponse.class)
        .retryWhen(
            RetryStrategies.defaultRetryStrategy(meterRegistry, "fetchOrCreatePseudonymsOnGpas"))
        .doOnError(e -> log.error("Unable to fetch pseudonym from gPAS: {}", e.getMessage()))
        .doOnNext(r -> log.trace("$pseudonymizeAllowCreate response: {}", r.parameter()))
        .map(GpasParameterResponse::getMappedID)
        .map(map -> map.get(id));
  }

  private static Mono<Throwable> handleGpasBadRequest(ClientResponse r) {
    return r.bodyToMono(OperationOutcome.class)
        .flatMap(
            b -> {
              var diagnostics = b.getIssueFirstRep().getDiagnostics();
              log.error("Bad Request: {}", diagnostics);
              if (diagnostics != null && diagnostics.startsWith("Unknown domain")) {
                return Mono.error(new UnknownDomainException(diagnostics));
              } else {
                return Mono.error(new IllegalArgumentException(diagnostics));
              }
            });
  }
}
