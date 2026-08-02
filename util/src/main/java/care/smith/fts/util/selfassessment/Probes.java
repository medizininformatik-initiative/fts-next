package care.smith.fts.util.selfassessment;

import static care.smith.fts.util.fhir.FhirClientUtils.fetchCapabilityStatementOperations;
import static care.smith.fts.util.fhir.FhirClientUtils.verifyOperationsExist;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

public interface Probes {

  Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

  static Mono<ComponentStatus> probeFhirCapability(
      WebClient client, String name, String url, Duration timeout, List<String> requiredOps) {
    long start = System.nanoTime();
    return fetchCapabilityStatementOperations(client)
        .map(
            cs -> {
              long elapsed = elapsedMs(start);
              if (requiredOps != null
                  && !requiredOps.isEmpty()
                  && !verifyOperationsExist(cs, requiredOps)) {
                return ComponentStatus.degraded(
                    name, "fhir", url, elapsed, "missing required operations: " + requiredOps);
              }
              return ComponentStatus.up(name, "fhir", url, elapsed);
            })
        .timeout(timeout)
        .onErrorResume(e -> Mono.just(ComponentStatus.down(name, "fhir", url, reason(e))));
  }

  static Mono<ComponentStatus> probeActuatorHealth(
      WebClient client, String name, String kind, String url, Duration timeout) {
    long start = System.nanoTime();
    return client
        .get()
        .uri("/actuator/health")
        .retrieve()
        .bodyToMono(ActuatorHealth.class)
        .map(
            h ->
                "UP".equals(h.status())
                    ? ComponentStatus.up(name, kind, url, elapsedMs(start))
                    : ComponentStatus.degraded(
                        name, kind, url, elapsedMs(start), "status=" + h.status()))
        .timeout(timeout)
        .onErrorResume(e -> Mono.just(ComponentStatus.down(name, kind, url, reason(e))));
  }

  static Mono<ComponentStatus> probeGet(
      WebClient client, String path, String name, String kind, String url, Duration timeout) {
    long start = System.nanoTime();
    return client
        .get()
        .uri(path)
        .retrieve()
        .toBodilessEntity()
        .map(e -> ComponentStatus.up(name, kind, url, elapsedMs(start)))
        .timeout(timeout)
        .onErrorResume(e -> Mono.just(ComponentStatus.down(name, kind, url, reason(e))));
  }

  /**
   * Host-reachability probe: any HTTP response (including 4xx/5xx) counts as UP. Only connection
   * failures and timeouts produce DOWN. Useful when probing endpoints behind auth where the probe
   * itself isn't authenticated.
   */
  static Mono<ComponentStatus> probeReachable(
      WebClient client, String name, String kind, String url, Duration timeout) {
    long start = System.nanoTime();
    return client
        .get()
        .exchangeToMono(
            response ->
                response
                    .releaseBody()
                    .thenReturn(ComponentStatus.up(name, kind, url, elapsedMs(start))))
        .timeout(timeout)
        .onErrorResume(e -> Mono.just(ComponentStatus.down(name, kind, url, reason(e))));
  }

  private static long elapsedMs(long startNs) {
    return (System.nanoTime() - startNs) / 1_000_000L;
  }

  private static String reason(Throwable e) {
    return Objects.toString(e.getMessage(), e.getClass().getSimpleName());
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ActuatorHealth(String status) {}
}
