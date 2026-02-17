package care.smith.fts.cda.impl;

import static care.smith.fts.cda.services.deidentifhir.DeidentifhirUtils.buildRegistry;
import static care.smith.fts.util.RetryStrategies.defaultRetryStrategy;

import care.smith.fts.api.ConsentedPatient;
import care.smith.fts.api.ConsentedPatientBundle;
import care.smith.fts.api.DateShiftPreserve;
import care.smith.fts.api.TransportBundle;
import care.smith.fts.api.cda.Deidentificator;
import care.smith.fts.cda.services.deidentifhir.DeidentifhirUtils;
import care.smith.fts.cda.services.deidentifhir.GeneratingReplacementProvider;
import care.smith.fts.util.error.TransferProcessException;
import care.smith.fts.util.tca.TcaDomains;
import care.smith.fts.util.tca.TransportMappingRequest;
import care.smith.fts.util.tca.TransportMappingResponse;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
class DeidentifhirStep implements Deidentificator {
  private final WebClient tcaClient;
  private final TcaDomains domains;
  private final Duration maxDateShift;
  private final DateShiftPreserve preserve;
  private final com.typesafe.config.Config config;
  private final MeterRegistry meterRegistry;

  public DeidentifhirStep(
      WebClient tcaClient,
      TcaDomains domains,
      Duration maxDateShift,
      DateShiftPreserve preserve,
      com.typesafe.config.Config config,
      MeterRegistry meterRegistry) {
    this.tcaClient = tcaClient;
    this.domains = domains;
    this.maxDateShift = maxDateShift;
    this.preserve = preserve;
    this.config = config;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public Mono<TransportBundle> deidentify(ConsentedPatientBundle bundle) {
    return Mono.defer(
        () -> {
          var patient = bundle.consentedPatient();
          var provider = new GeneratingReplacementProvider(patient.identifier());
          var registry = buildRegistry(provider);
          var deidentified =
              DeidentifhirUtils.deidentify(
                  config, registry, bundle.bundle(), patient.identifier(), meterRegistry);

          var idMappings = provider.getIdMappings();
          var dateMappings = provider.getDateMappings();
          return (idMappings.isEmpty() && dateMappings.isEmpty())
              ? Mono.empty()
              : sendMappingsToTca(patient, idMappings, dateMappings)
                  .map(transferId -> new TransportBundle(deidentified, transferId));
        });
  }

  private Mono<String> sendMappingsToTca(
      ConsentedPatient patient, Map<String, String> idMappings, Map<String, String> dateMappings) {
    var request =
        new TransportMappingRequest(
            patient.identifier(),
            patient.patientIdentifierSystem(),
            idMappings,
            dateMappings,
            domains,
            maxDateShift,
            preserve);

    log.trace(
        "Send transport mappings for {} IDs and {} dates to TCA",
        idMappings.size(),
        dateMappings.size());
    return tcaClient
        .post()
        .uri("/api/v2/cd/transport-mapping")
        .headers(h -> h.setContentType(MediaType.APPLICATION_JSON))
        .bodyValue(request)
        .retrieve()
        .onStatus(r -> r.equals(HttpStatus.BAD_REQUEST), DeidentifhirStep::handleBadRequest)
        .bodyToMono(TransportMappingResponse.class)
        .timeout(Duration.ofSeconds(30))
        .retryWhen(defaultRetryStrategy(meterRegistry, "sendMappingsToTca"))
        .doOnError(DeidentifhirStep::handleError)
        .map(TransportMappingResponse::transferId);
  }

  private static Mono<Throwable> handleBadRequest(ClientResponse s) {
    return s.bodyToMono(ProblemDetail.class)
        .flatMap(b -> Mono.error(new TransferProcessException(b.getDetail())));
  }

  private static void handleError(Throwable e) {
    log.error("Cannot send transport mappings to TCA: {}", e.getMessage());
  }
}
